package com.oasisfeng.island.util;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import com.oasisfeng.island.analytics.Analytics;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;

import static android.Manifest.permission.PACKAGE_USAGE_STATS;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.O;

/**
 * Permission-related helpers
 *
 * Created by Oasis on 2017/10/8.
 */
@ParametersAreNonnullByDefault
public class Permissions extends com.oasisfeng.android.content.pm.Permissions {

	private static final boolean TEST_NO_DEV_PERMISSIONS = false/* BuildConfig.DEBUG */;
	private static final int PID = Process.myPid();
	private static final int UID = Process.myUid();

	@TargetApi(M) @StringDef({ INTERACT_ACROSS_USERS, WRITE_SECURE_SETTINGS, PACKAGE_USAGE_STATS }) @interface DevPermission {}

	public static boolean has(final Context context, final String permission) { //noinspection SimplifiableIfStatement
		if (TEST_NO_DEV_PERMISSIONS && (INTERACT_ACROSS_USERS.equals(permission) || WRITE_SECURE_SETTINGS.equals(permission))) return false;
		return context.checkPermission(permission, PID, UID) == PackageManager.PERMISSION_GRANTED;
	}

	public static boolean ensure(final Context context, final @DevPermission String permission) {
		if (has(context, permission)) return true;
		if (SDK_INT < M || SDK_INT > O) return false;
		if (sGrantingDevPermissionAllowed == Boolean.FALSE) return false;

		return sGrantingDevPermissionAllowed = tryGranting(context, permission);
	}

	@RequiresApi(M) private static boolean tryGranting(final Context context, final String permission) {
		final String sp = Build.VERSION.SECURITY_PATCH;
		if (sp.contains("2018") || sp.contains("2017-11") || sp.contains("2017-12")) return false;	// No longer works after 2017.11 security patch. (CVE-2017-0830)

		if (Users.isOwner() && ! new DevicePolicies(context).isActiveDeviceOwner()) return false;
		if (Users.isProfileManagedByIsland() && ! new DevicePolicies(context).isProfileOwner()) return false;
		final boolean result = new DevicePolicies(context).invoke((dpm, admin) ->
				dpm.setPermissionGrantState(admin, context.getPackageName(), permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED));
		if (! result) Analytics.$().event("permission_failure").withRaw("permission", permission).withRaw("SP", sp).send();
		return result;
	}

	private static Boolean sGrantingDevPermissionAllowed;
}
