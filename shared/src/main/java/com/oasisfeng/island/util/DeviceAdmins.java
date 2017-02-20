package com.oasisfeng.island.util;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * Utility class for device-admin related functions
 *
 * Created by Oasis on 2017/2/19.
 */
public class DeviceAdmins {

	public static ComponentName getComponentName(final Context context) {
		if (sDeviceAdminComponent != null) return sDeviceAdminComponent;
		final List<ResolveInfo> admins = context.getPackageManager().queryBroadcastReceivers(
				new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED).setPackage(Modules.MODULE_ENGINE), 0);
		if (admins.size() != 1) throw new IllegalStateException("Engine module is not correctly installed.");
		return sDeviceAdminComponent = new ComponentName(Modules.MODULE_ENGINE, admins.get(0).activityInfo.name);
	}

	private static ComponentName sDeviceAdminComponent;
}
