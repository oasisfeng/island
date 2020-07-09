package com.oasisfeng.island.files;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.shuttle.ContextShuttle;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * Created by Oasis on 2018-6-8.
 */
public class IslandFiles {

	public static boolean isCompatible(final Context context) {
		return Users.hasProfile() && getProfilePackageManager(context) != null;
	}

	@RequiresPermission(Permissions.INTERACT_ACROSS_USERS) public static boolean isFileShuttleEnabled(final Context context) {
		final ComponentName component = Modules.getFileProviderComponent(context);
		return component != null && getProfilePackageManager(context).getComponentEnabledSetting(component) == COMPONENT_ENABLED_STATE_ENABLED;
	}

	/** @return true if activated synchronously, false for failure or asynchronous procedure. */
	@RequiresPermission(Permissions.INTERACT_ACROSS_USERS) public static boolean enableFileShuttle(final Activity activity) {
		if (Modules.getFileProviderComponent(activity) == null) {
			Toast.makeText(activity, "Module \"File Provider\" not installed.", Toast.LENGTH_LONG).show();
			return false;
		}
		Analytics.$().event("file_shuttle_request").send();
		com.oasisfeng.android.content.pm.Permissions.request(activity, WRITE_EXTERNAL_STORAGE, result -> {
			if (result == PERMISSION_GRANTED) onPermissionGranted(activity);
			else Toast.makeText(activity, R.string.toast_external_storage_permission_required, Toast.LENGTH_LONG).show();
		});
		return false;
	}

	private static void onPermissionGranted(final Context context) {
		Analytics.$().event("file_shuttle_enable").send();
		final ComponentName component = Modules.getFileProviderComponent(context);
		getProfilePackageManager(context).setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
		// FIXME: Not enabling shuttle in owner user, as ExternalStorageProviderProxy is not working as expected in manger profile.
		//setComponentEnabled(context.getPackageManager(), shuttle, all_met);
	}

	private static PackageManager getProfilePackageManager(final Context context) {
		return ContextShuttle.getPackageManagerAsUser(context, Users.profile);
	}
}
