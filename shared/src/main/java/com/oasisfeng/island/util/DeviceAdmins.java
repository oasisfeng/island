package com.oasisfeng.island.util;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.shared.BuildConfig;

import java.util.List;

import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;
import static java.util.Objects.requireNonNull;

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
		final List<ComponentName> active_admins = requireNonNull((DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE)).getActiveAdmins();
		if (active_admins != null && ! active_admins.isEmpty()) for (final ComponentName active_admin : active_admins)
			if (Modules.MODULE_ENGINE.equals(active_admin.getPackageName())) return active_admin;

		final Intent intent = new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED).setPackage(Modules.MODULE_ENGINE);
		final List<ResolveInfo> admins = context.getPackageManager().queryBroadcastReceivers(intent, PackageManager.GET_DISABLED_COMPONENTS);
		if (admins.size() == 1) {
			final ResolveInfo admin = admins.get(0);
			sDeviceAdminComponent = new ComponentName(Modules.MODULE_ENGINE, admins.get(0).activityInfo.name);
			if (! admin.activityInfo.enabled) {
				Analytics.$().event("device_admin_component_disabled").with(ITEM_ID, sDeviceAdminComponent.flattenToShortString()).send();
				context.getPackageManager().setComponentEnabledSetting(sDeviceAdminComponent, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
			}
			return sDeviceAdminComponent;
		}	// No resolve result on some Android 7.x devices, cause unknown.
		if (BuildConfig.DEBUG) throw new IllegalStateException("Engine module is not correctly installed: " + admins);
		return new ComponentName(Modules.MODULE_ENGINE, "com.oasisfeng.island.IslandDeviceAdminReceiver");	// Fallback
	}

	private static ComponentName sDeviceAdminComponent;
 }
