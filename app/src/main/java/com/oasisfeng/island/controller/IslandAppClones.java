package com.oasisfeng.island.controller;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.StrictMode;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.oasisfeng.android.widget.Toasts;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.DevicePolicies;

import java.io.File;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;

/**
 * Controller for complex procedures of Island.
 *
 * Refactored by Oasis on 2018-9-30.
 */
public class IslandAppClones {

	public static final int CLONE_RESULT_ALREADY_CLONED = 0;
	public static final int CLONE_RESULT_OK_INSTALL = 1;
	public static final int CLONE_RESULT_OK_INSTALL_EXISTING = 2;
	public static final int CLONE_RESULT_OK_GOOGLE_PLAY = 10;
	public static final int CLONE_RESULT_UNKNOWN_SYS_MARKET = 11;
	public static final int CLONE_RESULT_NOT_FOUND = -1;
	public static final int CLONE_RESULT_NO_SYS_MARKET = -2;

	private static final String SCHEME_PACKAGE = "package";

	/** Two-stage operation, because of pre-cloning user interaction, depending on the situation in managed profile. */
	public int cloneUserApp(final String pkg, final String apk_path, final boolean do_it) {
		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestrictionsIfNeeded(mContext, UserManager.DISALLOW_INSTALL_APPS);

		if (SDK_INT >= P && mContext.getSystemService(DevicePolicyManager.class).isAffiliatedUser()) try {
			if (! do_it) return CLONE_RESULT_OK_INSTALL_EXISTING;
			if (mDevicePolicies.invoke(DevicePolicyManager::installExistingPackage, pkg))
				return CLONE_RESULT_OK_INSTALL_EXISTING;
			Log.e(TAG, "Error cloning existent user app: " + pkg);								// Fall-through
		} catch (final SecurityException e) {
			Analytics.$().logAndReport(TAG, "Error cloning existent user app: " + pkg, e);	// Fall-through
		}

		if (! IslandManager.ensureLegacyInstallNonMarketAppAllowed(mContext, mDevicePolicies))
			return cloneUserAppViaMarketApp(pkg, do_it);

		final String my_pkg = mContext.getPackageName();
		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts(SCHEME_PACKAGE, pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, my_pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (SDK_INT >= O) intent.setData(Uri.fromFile(new File(apk_path)));
		mDevicePolicies.enableSystemApp(intent);				// Ensure package installer is enabled.

		if (! do_it) return CLONE_RESULT_OK_INSTALL;

		if (SDK_INT >= O) {
			final StrictMode.VmPolicy vm_policy = StrictMode.getVmPolicy();
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());		// Workaround to suppress FileUriExposedException.
			try {
				final Intent uas_manager = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts(SCHEME_PACKAGE, my_pkg, null));
				final PackageManager pm = mContext.getPackageManager();
				final ResolveInfo resolve;
				if (! pm.canRequestPackageInstalls() && (resolve = pm.resolveActivity(uas_manager, PackageManager.MATCH_SYSTEM_ONLY)) != null) {
					uas_manager.setComponent(new ComponentName(resolve.activityInfo.applicationInfo.packageName, resolve.activityInfo.name));
					Toasts.showLong(mContext, R.string.toast_enable_install_from_unknown_source);
					mContext.startActivities(new Intent[] { intent, uas_manager });
				} else mContext.startActivity(intent);
			} finally {
				StrictMode.setVmPolicy(vm_policy);
			}
		} else mContext.startActivity(intent);		// Launch package installer
		return CLONE_RESULT_OK_INSTALL;
	}

	private int cloneUserAppViaMarketApp(final String pkg, final boolean do_it) {
		// Launch market app (preferable Google Play Store)
		final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mDevicePolicies.enableSystemApp(market_intent);
		final ResolveInfo market_info = mContext.getPackageManager().resolveActivity(market_intent, SDK_INT < N ? 0 : PackageManager.MATCH_SYSTEM_ONLY);
		if (market_info == null || (market_info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) == 0)	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
			return CLONE_RESULT_NO_SYS_MARKET;

		if (WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.activityInfo.applicationInfo.packageName)) {
			if (do_it) {
				mDevicePolicies.enableSystemApp(WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
				mContext.startActivity(market_intent);
			}
			return CLONE_RESULT_OK_GOOGLE_PLAY;
		} else {
			if (do_it) mContext.startActivity(market_intent);
			return CLONE_RESULT_UNKNOWN_SYS_MARKET;
		}
	}

	public IslandAppClones(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;

	private static final String TAG = "Island.IC";
}
