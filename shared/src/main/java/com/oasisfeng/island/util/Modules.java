package com.oasisfeng.island.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oasisfeng.island.engine.CrossProfile;

import java.util.List;

import static android.content.Intent.ACTION_MAIN;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

/**
 * Utility class to get module ID of common modules in Island.
 *
 * Created by Oasis on 2017/2/19.
 */
public class Modules {

	// Engine is singleton across the device.
	public static final String MODULE_ENGINE = "com.oasisfeng.island";

	public static void broadcast(final Context context, final Intent intent) {
		if (intent.getComponent() != null || intent.getPackage() != null) throw new IllegalArgumentException("Explicit " + intent);
		final String[] pkgs = context.getPackageManager().getPackagesForUid(Process.myUid());
		if (pkgs == null) return;
		for (final String pkg : pkgs) context.sendBroadcast(intent.setPackage(pkg));
		intent.setPackage(null);
	}

	public static @Nullable ComponentName getFileProviderComponent(final Context context) {
		if (sFileProviderComponent != null) return sFileProviderComponent.getPackageName().isEmpty() ? null : sFileProviderComponent;
		final String my_pkg = context.getPackageName();
		final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE).setPackage(my_pkg);		// First query in current package
		final PackageManager pm = context.getPackageManager();
		List<ResolveInfo> resolves = pm.queryIntentContentProviders(intent, MATCH_DISABLED_COMPONENTS);
		if (! resolves.isEmpty()) {
			if (resolves.size() > 1) throw new IllegalStateException("More than 1 file provider: " + resolves);
			return sFileProviderComponent = new ComponentName(my_pkg, resolves.get(0).providerInfo.name);
		}
		resolves = pm.queryIntentContentProviders(intent.setPackage(null), MATCH_DISABLED_COMPONENTS);	// Query in UID-shared packages
		final int my_uid = Process.myUid();
		for (final ResolveInfo resolve : resolves) {
			final ProviderInfo provider = resolve.providerInfo;
			if (provider.applicationInfo.uid != my_uid) continue;
			return sFileProviderComponent = new ComponentName(provider.packageName, provider.name);
		}
		sFileProviderComponent = new ComponentName("", "");
		return null;
	}

	public static @NonNull ComponentName getMainLaunchActivity(final Context context) {
		final Intent intent = new Intent(ACTION_MAIN).addCategory(CrossProfile.CATEGORY_PARENT_PROFILE);
		final int uid = Process.myUid();
		for (final ResolveInfo resolve : queryActivities(context, intent)) {
			final ActivityInfo activity = resolve.activityInfo;
			if (activity.applicationInfo.uid == uid) return new ComponentName(activity.packageName, activity.name);
		}
		throw new IllegalStateException("UI module not installed");
	}

	private static List<ResolveInfo> queryActivities(final Context context, final Intent intent) {
		return context.getPackageManager().queryIntentActivities(intent, MATCH_DISABLED_COMPONENTS | MATCH_DEFAULT_ONLY);
	}

	private static ComponentName sFileProviderComponent;
}
