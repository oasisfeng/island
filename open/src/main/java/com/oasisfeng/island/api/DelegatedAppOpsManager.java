package com.oasisfeng.island.api;

import android.app.AppOpsManager;
import android.app.DerivedAppOpsManager;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.oasisfeng.android.annotation.UserIdInt;
import com.oasisfeng.android.os.UserHandles;
import com.oasisfeng.hack.Hack;
import com.oasisfeng.island.RestrictedBinderProxy;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.Context.APP_OPS_SERVICE;
import static com.oasisfeng.island.ApiConstants.DELEGATION_APP_OPS;

/**
 * Delegated {@link AppOpsManager}
 *
 * Created by Oasis on 2019-4-30.
 */
public class DelegatedAppOpsManager extends DerivedAppOpsManager {

	public static Binder buildBinderProxy(final Context context) throws ReflectiveOperationException {
		return new DelegatedAppOpsManager(context).mBinderProxy;
	}

	private DelegatedAppOpsManager(final Context context) throws ReflectiveOperationException {
		mBinderProxy = sHelper.inject(this, context, APP_OPS_SERVICE, (c, delegate) -> new AppOpsBinderProxy(c, DELEGATION_APP_OPS, delegate));

		// Whiltelist supported APIs by invoking them (with dummy arguments) before seal().
		final Hacks.AppOpsManager aom = Hack.into(this).with(Hacks.AppOpsManager.class);
		aom.setMode(0, 0, "a.b.c", 0);
		aom.getOpsForPackage(0, "a.b.c", new int[]{ 0 });
		aom.getPackagesForOps(new int[]{ 0 });
		mBinderProxy.seal();
	}

	private final AppOpsBinderProxy mBinderProxy;

	private static final DerivedManagerHelper<AppOpsManager> sHelper = new DerivedManagerHelper<>(AppOpsManager.class);

	private class AppOpsBinderProxy extends RestrictedBinderProxy {

		@Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
			if (! isSealed() && mCodeSetMode == - 1) mCodeSetMode = code;
			return super.onTransact(code, data, reply, flags);
		}

		@Override protected boolean doTransact(final int code, final Parcel data, final Parcel reply, final int flags) throws RemoteException {
			if (code != mCodeSetMode) return super.doTransact(code, data, reply, flags);

			final int pos = data.dataPosition();
			data.enforceInterface(DESCRIPTOR);
			final int ops = data.readInt();
			final int uid = data.readInt();

			final @UserIdInt int user_id = UserHandles.getUserId(uid);
			if (user_id == UserHandles.getIdentifier(Users.current())) {
				data.setDataPosition(pos);
				return super.doTransact(code, data, reply, flags);
			}
			if (Users.profile == null || user_id != UserHandles.getIdentifier(Users.profile))
				throw new IllegalArgumentException("User " + user_id + " is not managed by Island");

			final String pkg = data.readString();
			final int mode = data.readInt();
			data.setDataPosition(pos);
			try {	// Cross-profile synchronized invocation with 2s timeout.
				MethodShuttle.runInProfile(mContext, context -> {
					Hack.into(context.getSystemService(APP_OPS_SERVICE)).with(Hacks.AppOpsManager.class).setMode(ops, uid, pkg, mode);
				}).toCompletableFuture().get(2, TimeUnit.SECONDS);
			} catch (final ExecutionException e) {
				final Throwable cause = e.getCause();
				if (cause instanceof RuntimeException) throw (RuntimeException) cause;
				if (cause instanceof RemoteException) throw (RemoteException) cause;
				throw new RemoteException("Failed to setMode() due to " + e.getCause());
			} catch (final InterruptedException | TimeoutException ignored) {}
			return true;
		}

		AppOpsBinderProxy(final Context context, final String delegation, final IBinder delegate) { super(context, delegation, delegate); }

		private int mCodeSetMode = -1;
		private static final java.lang.String DESCRIPTOR = "com.android.internal.app.IAppOpsService";
	}

	private static final String TAG = "Island.DAOM";
}
