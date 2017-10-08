package com.oasisfeng.island.util;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java9.util.Optional;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Permission-related helpers
 *
 * Created by Oasis on 2017/10/8.
 */
public class Permissions {

	public static boolean has(final Context context, final String permission) {
		return context.checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED;
	}

	public static boolean ensure(final Context context, final String permission) {
		if (has(context, permission)) return true;
		if (SDK_INT < M) return false;
		if (Users.isOwner() && ! new DevicePolicies(context).isDeviceOwner()) return false;
		if (Users.isProfile()) {
			final Optional<Boolean> is_owner = DevicePolicies.isProfileOwner(context);
			if (is_owner == null || ! is_owner.orElse(false)) return false;
		}
		return new DevicePolicies(context).setPermissionGrantState(context.getPackageName(), permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
	}
}
