package com.oasisfeng.island.api;

import android.app.admin.DerivedDevicePolicyManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Binder;

import com.oasisfeng.island.ApiConstants;
import com.oasisfeng.island.RestrictedBinderProxy;

import static android.content.Context.DEVICE_POLICY_SERVICE;

/**
 * Delegated {@link DevicePolicyManager}
 *
 * Created by Oasis on 2019-4-30.
 */
public class DelegatedDevicePolicyManager extends DerivedDevicePolicyManager {

	public static Binder buildBinderProxy(final Context context) throws ReflectiveOperationException {
		return new DelegatedDevicePolicyManager(context).mBinderProxy;
	}

	private DelegatedDevicePolicyManager(final Context c) throws ReflectiveOperationException {
		mBinderProxy = sHelper.inject(this, c, DEVICE_POLICY_SERVICE, ApiConstants.DELEGATION_PACKAGE_ACCESS);

		// Whiltelist supported APIs by invoking them (with dummy arguments) before seal().
		setApplicationHidden(null, null, false);
		isApplicationHidden(null, null);
		mBinderProxy.seal();
	}

	private final RestrictedBinderProxy mBinderProxy;

	private static final DerivedManagerHelper<DevicePolicyManager> sHelper = new DerivedManagerHelper<>(DevicePolicyManager.class);
}
