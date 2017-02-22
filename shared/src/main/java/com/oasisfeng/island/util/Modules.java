package com.oasisfeng.island.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.annotation.NonNull;
import android.support.compat.BuildConfig;

import com.google.common.base.Preconditions;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

/**
 * Utility class to get module ID of common modules in Island.
 *
 * Created by Oasis on 2017/2/19.
 */
public class Modules {

	// Engine is singleton across the device.
	public static final String MODULE_ENGINE = "com.oasisfeng.island";

	// The unique UI module for release version.
	private static final String MODULE_DEBUG_UI = "com.oasisfeng.island.mobile";

	public static @NonNull ComponentName getMainLaunchActivity(final Context context) {
		if (sMainLaunchActivity != null) return sMainLaunchActivity;
		sMainLaunchActivity = resolveMainActivityInPackage(context.getPackageManager(), context.getPackageName());
		if (BuildConfig.DEBUG && sMainLaunchActivity == null)
			sMainLaunchActivity = resolveMainActivityInPackage(context.getPackageManager(), MODULE_DEBUG_UI);
		return Preconditions.checkNotNull(sMainLaunchActivity, "UI module not installed");
	}

	private static ComponentName resolveMainActivityInPackage(final PackageManager pm, final String pkg) {
		final Intent intent = new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setPackage(pkg);
		@SuppressWarnings("deprecation") final ResolveInfo resolved = pm.resolveActivity(intent,
				MATCH_DEFAULT_ONLY | PackageManager.GET_DISABLED_COMPONENTS | PackageManager.GET_UNINSTALLED_PACKAGES);
		return resolved == null ? null : new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name);
	}
	private static ComponentName sMainLaunchActivity;
}
