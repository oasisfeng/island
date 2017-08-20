package com.oasisfeng.island.util;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import com.oasisfeng.island.analytics.Analytics;

import java.util.List;

/**
 * Utility class for device-admin related functions
 *
 * Created by Oasis on 2017/2/19.
 */
public class DeviceAdmins {

	public static ComponentName getComponentName(final Context context) {
		if (sDeviceAdminComponent == null) sDeviceAdminComponent = queryComponentName(context);
		return sDeviceAdminComponent;
	}

	private static ComponentName queryComponentName(final Context context) {
		final List<ComponentName> active_admins = ((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE)).getActiveAdmins();
		if (active_admins != null && ! active_admins.isEmpty()) for (final ComponentName active_admin : active_admins)
			if (Modules.MODULE_ENGINE.equals(active_admin.getPackageName())) return active_admin;

		try {
			final List<ResolveInfo> admins = context.getPackageManager().queryBroadcastReceivers(
					new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED).setPackage(Modules.MODULE_ENGINE), 0);
			if (admins.size() != 1) throw new IllegalStateException("Engine module is not correctly installed: " + admins);
			return sDeviceAdminComponent = new ComponentName(Modules.MODULE_ENGINE, admins.get(0).activityInfo.name);
		} catch (final SecurityException e) {
			Analytics.$().report(e);
			return new ComponentName(Modules.MODULE_ENGINE, "com.oasisfeng.island.IslandDeviceAdminReceiver");	// Fallback
		}
	}

	private static ComponentName sDeviceAdminComponent;

	private static final String TAG = DeviceAdmins.class.getSimpleName();
}
