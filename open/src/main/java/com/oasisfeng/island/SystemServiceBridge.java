package com.oasisfeng.island;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.island.api.DelegatedAppOpsManager;
import com.oasisfeng.island.api.DelegatedDevicePolicyManager;
import com.oasisfeng.island.api.PermissionForwardingRestrictionsManager;

import java.util.Objects;

import androidx.annotation.Nullable;

/**
 * Created by Oasis on 2019-6-5.
 */
public class SystemServiceBridge extends Service {

	@Nullable @Override public IBinder onBind(final Intent intent) {
		Log.v(TAG, "onBind: " + intent);
		// This check essentially verifies action and scheme without hardcoded string.
		final ResolveInfo service = getPackageManager().resolveService(new Intent(intent.getAction(), intent.getData()).setPackage(getPackageName()), 0);
		if (service == null || ! getClass().getName().equals(service.serviceInfo.name)) return null;

		final String ssp = Objects.requireNonNull(intent.getData()).getSchemeSpecificPart();
		final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
		if (ssp != null) switch (ssp) {
		case Context.RESTRICTIONS_SERVICE:
			try {
				return PermissionForwardingRestrictionsManager.buildBinderProxy(this, user);
			} catch (final ReflectiveOperationException e) {
				Log.e(TAG, "Error preparing delegated RestrictionsManager", e);
			} catch (final PermissionForwardingRestrictionsManager.IncompatibilityException e) {
				Log.e(TAG, "Incompatible ROM");
			}
			break;
		case Context.DEVICE_POLICY_SERVICE:
			try {
				return DelegatedDevicePolicyManager.buildBinderProxy(this);
			} catch (final ReflectiveOperationException e) {
				Log.e(TAG, "Error preparing delegated DevicePolicyManager", e);
				break;
			}
		case Context.APP_OPS_SERVICE:
			try {
				return DelegatedAppOpsManager.buildBinderProxy(this);
			} catch (final ReflectiveOperationException e) {
				Log.e(TAG, "Error preparing delegated AppOpsManager", e);
				break;
			}

		default: Log.w(TAG, "Unsupported system service: " + ssp);
		}
		return null;
	}

	private static final String TAG = "Island.SSB";
}
