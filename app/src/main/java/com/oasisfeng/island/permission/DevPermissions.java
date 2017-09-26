package com.oasisfeng.island.permission;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.RequiresApi;

import com.oasisfeng.island.util.DevicePolicies;

import java.util.Collections;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Manage development permissions.
 *
 * Created by Oasis on 2017/9/20.
 */
public class DevPermissions {

	/** All dev permissions excluding AppOp ones. */
	public static Set<String> getAllDevPermissions(final Context context) {
		if (sDevPermissions == null) sDevPermissions = queryAllDevPermissions(context);
		return sDevPermissions;
	}

	private static Set<String> queryAllDevPermissions(final Context context) {
		try {
			return StreamSupport.stream(context.getPackageManager().queryPermissionsByGroup(null, 0))
					.filter(info -> (info.protectionLevel & (PROTECTION_FLAG_DEVELOPMENT | PROTECTION_FLAG_APPOP)) == PROTECTION_FLAG_DEVELOPMENT)
					.map(info -> info.name).collect(Collectors.toSet());
		} catch (final PackageManager.NameNotFoundException e) {
			return Collections.emptySet();
		}
	}

	public static boolean hasManageableDevPermissions(final Context context, final String pkg) {
		if (SDK_INT < M) return false;
		final Set<String> all_dev_permissions = DevPermissions.getAllDevPermissions(context);
		try {
			final PackageInfo info = context.getPackageManager().getPackageInfo(pkg, GET_PERMISSIONS | GET_UNINSTALLED_PACKAGES);
			if (info.applicationInfo.targetSdkVersion < M) return false;	// We cannot manage dev permissions for app targeting pre-M.
			if (info.requestedPermissions == null) return false;
			for (final String permission : info.requestedPermissions) if (all_dev_permissions.contains(permission)) return true;
		} catch (final PackageManager.NameNotFoundException ignored) {}
		return false;
	}

	@RequiresApi(M) public static boolean setDevPermissionGrantState(final Context context, final String pkg, final String permission, final boolean granted) {
		final DevicePolicies policies = new DevicePolicies(context);
		final boolean result = policies.setPermissionGrantState(pkg, permission,
				granted ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
		if (! result) return false;
		policies.setPermissionGrantState(pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
		return true;
	}

	private static Set<String> sDevPermissions;
}
