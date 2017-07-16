package com.oasisfeng.island.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.GET_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

/**
 * Utility class to get module ID of common modules in Island.
 *
 * Created by Oasis on 2017/2/19.
 */
public class Modules {

	// Engine is singleton across the device.
	public static final String MODULE_ENGINE = "com.oasisfeng.island";

	public static @NonNull ComponentName getMainLaunchActivity(final Context context) {
		final Intent intent = new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setPackage(context.getPackageName());
		final ComponentName launcher_activity = resolveActivity(context, intent);
		//if (BuildConfig.DEBUG && launcher_activity == null)
			// TODO: Find launcher activity across all packages with the same UID.
		return Preconditions.checkNotNull(launcher_activity, "UI module not installed");
	}

	private static ComponentName resolveActivity(final Context context, final Intent intent) {
		final ResolveInfo resolved = context.getPackageManager().resolveActivity(intent,MATCH_DEFAULT_ONLY | GET_DISABLED_COMPONENTS | GET_UNINSTALLED_PACKAGES);
		return resolved == null ? null : new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name);
	}
}
