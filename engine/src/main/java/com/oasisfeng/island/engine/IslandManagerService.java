package com.oasisfeng.island.engine;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.util.DevicePolicies;

import java.util.List;
import java.util.Map;

import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static android.Manifest.permission.PACKAGE_USAGE_STATS;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.content.Context.USAGE_STATS_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManagerService extends IIslandManager.Stub {

	public IslandManagerService(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
		mLauncherApps = Suppliers.memoize(() -> (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE));
	}

	// TODO: Use ParceledSlickList instead of List
	@Override public List<ApplicationInfo> queryApps(final int query_flags, final int include_flags) {
		final List<ApplicationInfo> apps = mContext.getPackageManager().getInstalledApplications(query_flags);
		if (include_flags == 0) return apps;
		return StreamSupport.stream(apps).filter(app -> (app.flags & include_flags) != 0).collect(Collectors.toList());
	}

	@Override public ApplicationInfo getApplicationInfo(final String pkg, final int flags) {
		try {
			return mContext.getPackageManager().getApplicationInfo(pkg, flags);
		} catch (final PackageManager.NameNotFoundException e) { return null; }
	}

	@Override public boolean freezeApp(final String pkg, final String reason) {
		Log.i(TAG, "Freeze: " + pkg + " for " + reason);
		return mDevicePolicies.setApplicationHidden(pkg, true) || mDevicePolicies.isApplicationHidden(pkg);
	}

	@Override public boolean unfreezeApp(final String pkg) {
		Log.i(TAG, "Defreeze: " + pkg);
		return mDevicePolicies.setApplicationHidden(pkg, false) || ! mDevicePolicies.isApplicationHidden(pkg);
	}

	@Override public boolean launchApp(final String pkg) {
		if (mDevicePolicies.isApplicationHidden(pkg)) {		// Hidden or not installed
			if (! mDevicePolicies.setApplicationHidden(pkg, false))
				if (! Apps.of(mContext).isInstalledInCurrentUser(pkg)) return false;	// Not installed in profile, just give up.
		}
		try {
			if (mDevicePolicies.isPackageSuspended(pkg))
				mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		} catch (final PackageManager.NameNotFoundException ignored) {
			return false;
		}

		final Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(pkg);
		if (intent == null) return false;
		intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			mContext.startActivity(intent);
		} catch (final ActivityNotFoundException e) {
			return false;
		}
		return true;
	}

	@Override public int cloneApp(final String pkg, final boolean do_it) {
		final PackageManager pm = mContext.getPackageManager();
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
			final ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			return cloneApp(info, do_it);
		} catch (final PackageManager.NameNotFoundException ignored) { return IslandManager.CLONE_RESULT_NOT_FOUND; }
	}

	private int cloneApp(final ApplicationInfo app_info, final boolean do_it) {
		final String pkg = app_info.packageName;
		if ((app_info.flags & FLAG_INSTALLED) != 0) {
			Log.e(TAG, "Already cloned: " + pkg);
			return IslandManager.CLONE_RESULT_ALREADY_CLONED;
		}

		// System apps can be enabled by DevicePolicyManager.enableSystemApp(), which calls installExistingPackage().
		if ((app_info.flags & FLAG_SYSTEM) != 0) {
			if (do_it) enableSystemApp(pkg);
			return IslandManager.CLONE_RESULT_OK_SYS_APP;
		}

		/* For non-system app, we initiate the manual installation process. */

		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_APPS);

		if (ensureInstallNonMarketAppAllowed()) {
			mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
			final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
					.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, mContext.getPackageName());
			enableSystemAppForActivity(intent);				// Ensure package installer is enabled.
			if (do_it) mContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));		// Launch package installer
//			activity.startActivityForResult(intent.putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_CODE_INSTALL);
			return IslandManager.CLONE_RESULT_OK_INSTALL;
		}

		// Launch market app (preferable Google Play Store)
		final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		enableSystemAppForActivity(market_intent);
		final ActivityInfo market_info = market_intent.resolveActivityInfo(mContext.getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY);
		if (market_info == null || (market_info.applicationInfo.flags & FLAG_SYSTEM) == 0)	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
			return IslandManager.CLONE_RESULT_NO_SYS_MARKET;

		if (SystemAppsManager.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.applicationInfo.packageName)) {
			if (do_it) {
				enableSystemApp(SystemAppsManager.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
				mContext.startActivity(market_intent);
			}
			return IslandManager.CLONE_RESULT_OK_GOOGLE_PLAY;
		} else {
			if (do_it) mContext.startActivity(market_intent);
			return IslandManager.CLONE_RESULT_UNKNOWN_SYS_MARKET;
		}
	}

	private boolean ensureInstallNonMarketAppAllowed() {
		if (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0) return true;
		// We cannot directly enable this secure setting on Android 5.0.x.
		if (SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return false;

		mDevicePolicies.setSecureSetting(Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
		return Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
	}

	@Override public boolean removeClone(final String pkg) {
		final int flags;
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
			final ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			flags = info.flags;
		} catch (final PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Try to remove non-existent clone: " + pkg);
			return false;
		}
		if ((flags & FLAG_SYSTEM) != 0) {
			unfreezeApp(pkg);	// App must not be hidden for startAppDetailsActivity() to work.
			showAppSettingActivity(pkg);
			Analytics.$().event("action_disable_sys_app").with("package", pkg).send();
			return false;		// TODO: Separate return value
		}
		Activities.startActivity(mContext, new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.fromParts("package", pkg, null)));
		return true;
	}

	private void showAppSettingActivity(final String pkg) {
		enableSystemAppForActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", "", null)));
		mLauncherApps.get().startAppDetailsActivity(new ComponentName(pkg, ""), Process.myUserHandle(), null, null);
	}

	@Override public boolean block(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, true);
		return failed == null || failed.length == 0;
	}

	@Override public boolean unblock(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		return failed == null || failed.length == 0;
	}

	@Override public void destroyProfile() {
		mDevicePolicies.clearCrossProfileIntentFilters();
		mDevicePolicies.getManager().wipeData(0);
	}

	@Override public String[] queryUsedPackagesDuring(final long begin_time, final long end_time) {
		if (ContextCompat.checkSelfPermission(mContext, PACKAGE_USAGE_STATS) != PERMISSION_GRANTED) {
			if (SDK_INT < M) return new String[0];
			if (! mDevicePolicies.setPermissionGrantState(mContext.getPackageName(), PACKAGE_USAGE_STATS, PERMISSION_GRANT_STATE_GRANTED))
				return new String[0];
		}
		@SuppressLint("InlinedApi") final UsageStatsManager usm = (UsageStatsManager) mContext.getSystemService(USAGE_STATS_SERVICE); /* hidden but accessible on API 21 */
		final Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(begin_time, end_time);
		if (stats == null) return new String[0];
		final List<String> used_pkgs = StreamSupport.stream(stats.values()).filter(usage -> usage.getLastTimeUsed() != 0)
				.map(UsageStats::getPackageName).collect(Collectors.toList());
		return used_pkgs.toArray(new String[used_pkgs.size()]);
	}

	void enableSystemAppForActivity(final Intent intent) {
		try {
			final int result = mDevicePolicies.enableSystemApp(intent);
			if (result > 0) Log.d(TAG, result + " system apps enabled for: " + intent);
		} catch (final IllegalArgumentException e) {
			// This exception may be thrown on Android 5.x (but not 6.0+) if non-system apps also match this intent.
			// System apps should have been enabled before this exception is thrown, so we just ignore it.
			Log.w(TAG, "System apps may not be enabled for: " + intent);
		}
	}

	private void enableSystemApp(final String pkg) {
		try {
			mDevicePolicies.enableSystemApp(pkg);
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "Failed to enable: " + pkg, e);
		}
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;
	private final Supplier<LauncherApps> mLauncherApps;

	private static final String TAG = IslandManagerService.class.getSimpleName();

	public static class AidlService extends Service {

		@Nullable @Override public IBinder onBind(final Intent intent) {
			if (! GlobalStatus.running_in_owner) IslandProvisioning.startProfileOwnerProvisioningIfNeeded(this);
			return mStub.get();
		}

		private final Supplier<IslandManagerService> mStub = () -> new IslandManagerService(this);
	}
}
