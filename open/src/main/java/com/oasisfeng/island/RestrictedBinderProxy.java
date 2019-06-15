package com.oasisfeng.island;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.SparseArray;

import com.oasisfeng.island.util.DevicePolicies;

import java.io.FileDescriptor;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by Oasis on 2019-4-29.
 */
public class RestrictedBinderProxy extends Binder {

	public void seal() { mSealed = true; }
	protected boolean isSealed() { return mSealed; }
	public IBinder getDelegate() { return mDelegate; }

	@Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
		final int index = code - FIRST_CALL_TRANSACTION;
		if (! mSealed) {	// In unsealed state, invoked codes are recorded as allowed ones in later invocation.
			mAllowedCode.ensureCapacity(index + 1);
			while (mAllowedCode.size() <= index) mAllowedCode.add(null);	// Ensure actual size
			mAllowedCode.set(index, Boolean.TRUE);
			return true;
		} else {
			if (index >= mAllowedCode.size() || mAllowedCode.get(index) != Boolean.TRUE) throw new SecurityException("Unauthorized");
			if (! checkCallingDelegation()) throw new SecurityException("Require delegation authorization: " + mRequiredDelegation);
			return mDelegate.transact(code, data, reply, flags);
		}
	}

	private boolean checkCallingDelegation() {
		if (mRequiredDelegation == null) return true;
		final int uid = Binder.getCallingUid();
		Boolean allowed = mUidVerificationCache.get(uid);
		if (allowed != null) return allowed;

		final String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
		if (pkgs == null) return false;		// Should hardly happen

		allowed = false;
		for (final String pkg : pkgs) allowed = mDelegationManager.isDelegationAuthorized(pkg, Binder.getCallingUserHandle(), mRequiredDelegation);
		mUidVerificationCache.put(uid, allowed);
		return allowed;
	}

	public RestrictedBinderProxy(final Context context, final @Nullable String delegation, final IBinder delegate) {
		mContext = context;
		mDelegate = delegate;
		mRequiredDelegation = delegation;
		mDelegationManager = new DelegationManager(new DevicePolicies(context));
		try {
			attachInterface(null, mDelegate.getInterfaceDescriptor());
		} catch (final RemoteException e) {
			throw new IllegalArgumentException("Invalid delegate: " + delegate, e);
		}
	}

	@Override public void dump(@NonNull final FileDescriptor fd, @Nullable final String[] args) {
		try { mDelegate.dump(fd, args); } catch (final RemoteException ignored) {}
	}
	@Override public void dumpAsync(@NonNull final FileDescriptor fd, @Nullable final String[] args) {
		try { mDelegate.dumpAsync(fd, args); } catch (final RemoteException ignored) {}
	}
	@Override public void linkToDeath(@NonNull final DeathRecipient recipient, final int flags) {
		try { mDelegate.linkToDeath(recipient, flags); } catch (final RemoteException ignored) {}
	}
	@Override public boolean unlinkToDeath(@NonNull final DeathRecipient recipient, final int flags) { return mDelegate.unlinkToDeath(recipient, flags); }
	@Override public boolean pingBinder() { return mDelegate.pingBinder(); }
	@Override public boolean isBinderAlive() { return mDelegate.isBinderAlive(); }
	@Override public @Nullable IInterface queryLocalInterface(@NonNull final String descriptor) { return mDelegate.queryLocalInterface(descriptor); }

	private final Context mContext;
	private final IBinder mDelegate;
	private final String mRequiredDelegation;
	private final DelegationManager mDelegationManager;
	private boolean mSealed;
	private final ArrayList<Boolean> mAllowedCode = new ArrayList<>();
	@SuppressLint("UseSparseArrays") private final SparseArray<Boolean> mUidVerificationCache = new SparseArray<>();
}
