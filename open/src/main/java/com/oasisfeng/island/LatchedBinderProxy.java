package com.oasisfeng.island;

import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.FileDescriptor;
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
public class LatchedBinderProxy extends Binder {

	void setDelegate(final IBinder delegate) { mDelegate.complete(delegate); }

	void cancel(final @Nullable Throwable e) {
		if (e != null) mDelegate.completeExceptionally(e);
		else mDelegate.cancel(false);
	}

	@Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
		return delegateThrows().transact(code, data, reply, flags);
	}

	private IBinder delegateThrows() throws RemoteException {
		try {
			return mDelegate.get();
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

	private final CompletableFuture<IBinder> mDelegate = new CompletableFuture<>();
}
