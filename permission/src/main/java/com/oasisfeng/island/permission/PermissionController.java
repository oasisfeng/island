package com.oasisfeng.island.permission;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.support.annotation.RequiresApi;

import com.oasisfeng.island.util.DevicePolicies;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.M;

/**
 * The permission controlling routine.
 *
 * Created by Oasis on 2017/8/15.
 */
@RequiresApi(M)
public class PermissionController {

	public static boolean setPermissionGrantState(final Context context, final String pkg, final String permission, final boolean grant_or_revoke) {
		final DevicePolicies policies = new DevicePolicies(context);
		if (policies.getPermissionGrantState(pkg, permission) == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
			final int state = context.getPackageManager().checkPermission(permission, pkg);
			if (state == PERMISSION_GRANTED && grant_or_revoke || state == PERMISSION_DENIED && ! grant_or_revoke) return true;
		}
		final int new_state = grant_or_revoke ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
		if (! policies.setPermissionGrantState(pkg, permission, new_state)) return false;
		// Set to default state to clear the "POLICY FIXED" flags on permission state.
		return policies.setPermissionGrantState(pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
	}

	public static CharSequence loadShortActionDescription(final Context context, final String name) {
		if (android.Manifest.permission.WRITE_SECURE_SETTINGS.equals(name)) return context.getString(R.string.permlab_writeSecureSettings);
		if (android.Manifest.permission.DUMP.equals(name)) return context.getString(R.string.permlab_dump);
		if (android.Manifest.permission.READ_LOGS.equals(name)) return context.getString(R.string.permlab_readLogs);
		if (android.Manifest.permission.BATTERY_STATS.equals(name)) return context.getString(R.string.permlab_batteryStats);
		if ("android.permission.GET_APP_OPS_STATS".equals(name)) return context.getString(R.string.permlab_getAppOpsStats);
		if ("android.permission.GET_PROCESS_STATE_AND_OOM_SCORE".equals(name)) return context.getString(R.string.permlab_dump); // FIXME: No official strings.
		if ("android.permission.INTERACT_ACROSS_USERS".equals(name)) return context.getString(R.string.permlab_interactAcrossUsers);
		if (android.Manifest.permission.CHANGE_CONFIGURATION.equals(name)) return context.getString(R.string.permlab_changeConfiguration);
		if (android.Manifest.permission.SET_PROCESS_LIMIT.equals(name)) return context.getString(R.string.permlab_setProcessLimit);
		if (android.Manifest.permission.SET_ALWAYS_FINISH.equals(name)) return context.getString(R.string.permlab_setAlwaysFinish);
		if (android.Manifest.permission.SET_ANIMATION_SCALE.equals(name)) return context.getString(R.string.permlab_setAnimationScale);
		if (android.Manifest.permission.SET_DEBUG_APP.equals(name)) return context.getString(R.string.permlab_setDebugApp);
		if (android.Manifest.permission.SIGNAL_PERSISTENT_PROCESSES.equals(name)) return context.getString(R.string.permlab_signalPersistentProcesses);
		return null;
	}
}
