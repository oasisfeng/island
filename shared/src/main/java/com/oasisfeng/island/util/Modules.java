package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.provider.DocumentsContract;

import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.GET_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

/**
 * Utility class to get module ID of common modules in Island.
 *
 * Created by Oasis on 2017/2/19.
 */
public class Modules {

	// Engine is singleton across the device.
	public static final String MODULE_ENGINE = "com.oasisfeng.island";

	public static @Nullable ComponentName getFileProviderComponent(final Context context) {
		if (sFileProviderComponent != null) return sFileProviderComponent.getPackageName().isEmpty() ? null : sFileProviderComponent;
		final String my_pkg = context.getPackageName();
		final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE).setPackage(my_pkg);		// First query in current package
		final PackageManager pm = context.getPackageManager();
		List<ResolveInfo> resolves = pm.queryIntentContentProviders(intent, GET_DISABLED_COMPONENTS);
		if (resolves != null && ! resolves.isEmpty()) {
			if (resolves.size() > 1) throw new IllegalStateException("More than 1 file provider: " + resolves);
			return sFileProviderComponent = new ComponentName(my_pkg, resolves.get(0).providerInfo.name);
		}
		resolves = pm.queryIntentContentProviders(intent.setPackage(null), GET_DISABLED_COMPONENTS);	// Query in UID-shared packages
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
		final Intent intent = new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setPackage(context.getPackageName());
		final ComponentName launcher_activity = resolveActivity(context, intent);
		//if (BuildConfig.DEBUG && launcher_activity == null)
			// TODO: Find launcher activity across all packages with the same UID.
		return Objects.requireNonNull(launcher_activity, "UI module not installed");
	}

	private static ComponentName resolveActivity(final Context context, final Intent intent) {
		@SuppressLint("WrongConstant") final ResolveInfo resolved = context.getPackageManager().resolveActivity(intent,
				MATCH_DEFAULT_ONLY | GET_DISABLED_COMPONENTS | Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED);
		return resolved == null ? null : new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name);
	}

	private static ComponentName sFileProviderComponent;
}
