package com.oasisfeng.island.api;

import android.content.Context;
import android.content.DerivedRestrictionsManager;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.android.content.pm.PackageManagerCompat;
import com.oasisfeng.island.DelegatedScopeAuthorization;
import com.oasisfeng.island.RestrictedBinderProxy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.Context.RESTRICTIONS_SERVICE;

/**
 * Allow client to call {@link RestrictionsManager#requestPermission(String, String, PersistableBundle)} in owner user when Island is not device owner.
 *
 * Created by Oasis on 2019-6-6.
 */
public class PermissionForwardingRestrictionsManager extends DerivedRestrictionsManager {

	public static Binder buildBinderProxy(final Context context, final UserHandle user) throws IncompatibilityException, ReflectiveOperationException {
		return new PermissionForwardingRestrictionsManager(context, user).mBinderProxy;
	}

	public static class IncompatibilityException extends Exception {}

	private void onPermissionRequest(final String pkg, final String type, final String id, final PersistableBundle bundle) {
		if (! mCompatibilityVerified) {
			if (mContext.getPackageName().equals(pkg) && DUMMY_TYPE.equals(type) && DUMMY_ID.equals(id) && bundle != null && bundle.getInt("") == -1) return;
			throw new IllegalStateException("Incompatible ROM");
		}
		Log.d(TAG, "onPermissionRequest");
		if (! ensureCallerMatchesPackage(pkg)) throw new SecurityException("Package name does not match caller");

		final Intent intent = new Intent(mContext, DelegatedScopeAuthorization.class).setAction(RestrictionsManager.ACTION_REQUEST_PERMISSION)
				.putExtra(RestrictionsManager.EXTRA_PACKAGE_NAME, pkg).putExtra(RestrictionsManager.EXTRA_REQUEST_TYPE, type)
				.putExtra(RestrictionsManager.EXTRA_REQUEST_ID, id).putExtra(RestrictionsManager.EXTRA_REQUEST_BUNDLE, bundle);
		mContext.sendBroadcast(intent);
	}

	private boolean ensureCallerMatchesPackage(final String pkg) {
		try {
			final int uid = new PackageManagerCompat(mContext).getPackageUid(pkg);		// TODO: Cache?
			return uid == Binder.getCallingUid();
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	private final boolean mCompatibilityVerified;

	public PermissionForwardingRestrictionsManager(final Context context, final UserHandle user) throws ReflectiveOperationException, IncompatibilityException {
		mContext = context;
		mUser = user;
		mBinderProxy = sHelper.inject(this, context, RESTRICTIONS_SERVICE, InterceptedBinderProxy::new);

		try {
			final PersistableBundle dummy_bundle = new PersistableBundle(); dummy_bundle.putInt("", -1);
			requestPermission(DUMMY_TYPE, DUMMY_ID, dummy_bundle);    	// For InterceptedBinderProxy to capture the actual code
			hasRestrictionsProvider();									// To whitelist
		} catch (final IllegalStateException e) {
			throw new IncompatibilityException();
		}
		mCompatibilityVerified = true;
		mBinderProxy.seal();
	}

	public RestrictedBinderProxy getDelegatedBinderProxy() { return mBinderProxy; }

	private final Context mContext;
	private final UserHandle mUser;
	private final InterceptedBinderProxy mBinderProxy;

	private static final DerivedManagerHelper<RestrictionsManager> sHelper = new DerivedManagerHelper<>(RestrictionsManager.class);

	private static final String DUMMY_TYPE = "_t", DUMMY_ID = "_i";

	class InterceptedBinderProxy extends RestrictedBinderProxy {

		InterceptedBinderProxy(final Context context, final IBinder delegate) {
			super(context, null, delegate);
		}

		@Override protected boolean onTransact(final int code, @NonNull final Parcel data, @Nullable final Parcel reply, final int flags) throws RemoteException {
			if (! isSealed()) {
				if (mCodeRequestPermission == 0) mCodeRequestPermission = code;
				else mCodeHasRestrictionsProvider = code;
			} else if (code == mCodeRequestPermission) {
				data.enforceInterface(DESCRIPTOR);
				onPermissionRequest(data.readString(), data.readString(), data.readString(),
						data.readInt() != 0 ? PersistableBundle.CREATOR.createFromParcel(data) : null);
				if (reply != null) reply.writeNoException();
			} else if (code == mCodeHasRestrictionsProvider) {
				data.enforceInterface(DESCRIPTOR);
				if (reply != null) {
					reply.writeNoException();
					reply.writeInt(1);		// Always return true for hasRestrictionsProvider()
				}
			} else return super.onTransact(code, data, reply, flags);
			return true;
		}

		private int mCodeRequestPermission;
		private int mCodeHasRestrictionsProvider;

		static final String DESCRIPTOR = "android.content.IRestrictionsManager";
	}

	@SuppressWarnings("SpellCheckingInspection") private static final String TAG = "Island.PFRM";
}
