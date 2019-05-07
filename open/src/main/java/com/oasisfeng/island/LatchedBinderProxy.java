package com.oasisfeng.island;

import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java9.util.concurrent.CompletableFuture;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Created by Oasis on 2019-5-3.
 */
public class LatchedBinderProxy extends Binder implements Parcelable {

	@Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
		return delegateThrows().transact(code, data, reply, flags);
	}

	private IBinder delegateThrows() throws RemoteException {
		try {
			return Objects.requireNonNull(mDelegate).get();
		} catch (final ExecutionException e) {
			final Throwable cause = e.getCause();
			throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
		} catch (final CancellationException e) {
			throw SDK_INT >= M ? new DeadObjectException("Failed to bind requested service") : new DeadObjectException();
		} catch (final InterruptedException e) {
			throw new RemoteException("Interrupted before binding");
		}
	}

	private IBinder delegate() {
		try {
			return delegateThrows();
		} catch (final RemoteException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override public void dump(@NonNull final FileDescriptor fd, @Nullable final String[] args) {
		try { delegate().dump(fd, args); } catch (final RemoteException ignored) {}
	}

	@Override public void dumpAsync(@NonNull final FileDescriptor fd, @Nullable final String[] args) {
		try { delegateThrows().dumpAsync(fd, args); } catch (final RemoteException ignored) {}
	}

	@Override public void linkToDeath(@NonNull final DeathRecipient recipient, final int flags) {
		try { delegateThrows().linkToDeath(recipient, flags); } catch (final RemoteException ignored) {}
	}

	@Override public boolean unlinkToDeath(@NonNull final DeathRecipient recipient, final int flags) { return delegate().unlinkToDeath(recipient, flags); }
	@Override public boolean pingBinder() { return delegate().pingBinder(); }
	@Override public boolean isBinderAlive() { return delegate().isBinderAlive(); }
	@Override public @Nullable IInterface queryLocalInterface(@NonNull final String descriptor) { return delegate().queryLocalInterface(descriptor); }

	void setDelegate(final IBinder delegate) {
		if (mDelegate != null) {
			mDelegate.complete(delegate);
			return;
		}
		final Parcel data = Parcel.obtain();
		try {
			data.writeNoException();
			data.writeStrongBinder(delegate);
			mRemote.transact(FIRST_CALL_TRANSACTION, data, null, FLAG_ONEWAY);
		} catch (final RemoteException e) {
			Log.e(TAG, "Error passing binder delegate", e);
		} finally {
			data.recycle();
		}
	}

	void cancel(final @Nullable Exception e) {
		if (mDelegate != null) {
			if (e != null) mDelegate.completeExceptionally(e);
			else mDelegate.cancel(false);
		} else {
			final Parcel data = Parcel.obtain();
			try {
				if (e == null) {
					data.writeNoException();
					data.writeStrongBinder(null);
				} else data.writeException(e);
				mRemote.transact(FIRST_CALL_TRANSACTION, data, null, FLAG_ONEWAY);
			} catch (final RemoteException re) {
				Log.e(TAG, "Error passing cancellation: " + e, re);
			} finally {
				data.recycle();
			}
		}
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeStrongBinder(mRemote = new Binder() { @Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
			if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
			try {
				data.readException();
				final IBinder delegate = data.readStrongBinder();
				if (delegate != null) setDelegate(delegate);
				else cancel(null);
			} catch (final Exception e) {
				cancel(e);
			}
			return true;
		}});
	}
	@Override public int describeContents() { return 0; }
	private LatchedBinderProxy(final Parcel in) { mRemote = in.readStrongBinder(); mDelegate = null; }

	LatchedBinderProxy() { mDelegate = new CompletableFuture<>(); mRemote = null; }

	private final @Nullable CompletableFuture<IBinder> mDelegate;
	private IBinder mRemote;

	public static final Creator<LatchedBinderProxy> CREATOR = new Creator<LatchedBinderProxy>() {
		@Override public LatchedBinderProxy createFromParcel(final Parcel in) { return new LatchedBinderProxy(in); }
		@Override public LatchedBinderProxy[] newArray(final int size) { return new LatchedBinderProxy[size]; }
	};

	private static final String TAG = "Island.LBP";
}
