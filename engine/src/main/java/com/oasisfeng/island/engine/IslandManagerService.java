package com.oasisfeng.island.engine;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.StrictMode;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.google.common.base.Supplier;
import com.oasisfeng.android.content.pm.Permissions;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.io.File;
import java.util.List;
import java.util.Map;

import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManagerService extends IIslandManager.Stub {

	public IslandManagerService(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
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
		Log.i(TAG, "Unfreeze: " + pkg);
		return mDevicePolicies.setApplicationHidden(pkg, false) || ! mDevicePolicies.isApplicationHidden(pkg);
	}

	@Override public boolean launchApp(final String pkg) {
		if (! Users.isOwner() || new DevicePolicies(mContext).isDeviceOwner()) {
			if (mDevicePolicies.isApplicationHidden(pkg)) {		// Hidden or not installed
				if (! mDevicePolicies.setApplicationHidden(pkg, false))
					if (! Apps.of(mContext).isInstalledInCurrentUser(pkg)) return false;	// Not installed in profile, just give up.
			}
			if (SDK_INT >= N) try {
				if (mDevicePolicies.isPackageSuspended(pkg))
					mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
			} catch (final PackageManager.NameNotFoundException ignored) {
				return false;
			}
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
		final String apk_path;
		try { @SuppressLint({"WrongConstant", "deprecation"})
			final ApplicationInfo app_info = pm.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES | Hacks.PackageManager_MATCH_ANY_USER);
			if ((app_info.flags & FLAG_INSTALLED) != 0) {
				Log.e(TAG, "Already cloned: " + pkg);
				return IslandManager.CLONE_RESULT_ALREADY_CLONED;
			}

			// System apps can be enabled by DevicePolicyManager.enableSystemApp(), which calls installExistingPackage().
			if ((app_info.flags & FLAG_SYSTEM) != 0) {
				if (do_it) enableSystemApp(pkg);
				return IslandManager.CLONE_RESULT_OK_SYS_APP;
			}
			apk_path = app_info.sourceDir;
		} catch (final PackageManager.NameNotFoundException ignored) {
			return IslandManager.CLONE_RESULT_NOT_FOUND;
		}

		/* For non-system app, we initiate the manual installation process. */

		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_APPS);

		if (ensureInstallNonMarketAppAllowed()) {
			mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
			final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
					.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, mContext.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			if (SDK_INT >= O) intent.setData(Uri.fromFile(new File(apk_path)));
			enableSystemAppForActivity(intent);				// Ensure package installer is enabled.
			if (do_it) {
				if (SDK_INT >= O) {		// Workaround to suppress FileUriExposedException.
					final StrictMode.VmPolicy vm_policy = StrictMode.getVmPolicy();
					StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
					mContext.startActivity(intent);
					StrictMode.setVmPolicy(vm_policy);
				} else mContext.startActivity(intent);		// Launch package installer
			}
			// TODO: activity.startActivityForResult(intent.putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_CODE_INSTALL);
			return IslandManager.CLONE_RESULT_OK_INSTALL;
		}

		// Launch market app (preferable Google Play Store)
		final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		enableSystemAppForActivity(market_intent);
		final ActivityInfo market_info = market_intent.resolveActivityInfo(mContext.getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY);
		if (market_info == null || (market_info.applicationInfo.flags & FLAG_SYSTEM) == 0)	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
			return IslandManager.CLONE_RESULT_NO_SYS_MARKET;

		if (WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.applicationInfo.packageName)) {
			if (do_it) {
				enableSystemApp(WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
				mContext.startActivity(market_intent);
			}
			return IslandManager.CLONE_RESULT_OK_GOOGLE_PLAY;
		} else {
			if (do_it) mContext.startActivity(market_intent);
			return IslandManager.CLONE_RESULT_UNKNOWN_SYS_MARKET;
		}
	}

	private boolean ensureInstallNonMarketAppAllowed() {
		if (SDK_INT >= O) return true;				// INSTALL_NON_MARKET_APPS is no longer supported and not required on Android O.
		if (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0) return true;
		if (SDK_INT < LOLLIPOP_MR1) return false;	// INSTALL_NON_MARKET_APPS is not whitelisted by DPM.setSecureSetting() until Android 5.1.

		mDevicePolicies.setSecureSetting(Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
		return Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
	}

	@RequiresApi(N) @Override public boolean block(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, true);
		return failed == null || failed.length == 0;
	}

	@RequiresApi(N) @Override public boolean unblock(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		return failed == null || failed.length == 0;
	}

	@Override public void destroyProfile() {
		mDevicePolicies.clearCrossProfileIntentFilters();
		mDevicePolicies.getManager().wipeData(0);
	}

	@Override public String[] queryUsedPackagesDuring(final long begin_time, final long end_time) {
		if (! Permissions.has(mContext, permission.PACKAGE_USAGE_STATS)) {
			if (SDK_INT < M) return new String[0];
			if (! mDevicePolicies.setPermissionGrantState(mContext.getPackageName(), permission.PACKAGE_USAGE_STATS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED))
				return new String[0];
		}
		@SuppressLint("InlinedApi") final UsageStatsManager usm = (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE); /* hidden but accessible on API 21 */
		final Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(begin_time, end_time);
		if (stats == null) return new String[0];
		final List<String> used_pkgs = StreamSupport.stream(stats.values()).filter(usage -> usage.getLastTimeUsed() != 0)
				.map(UsageStats::getPackageName).collect(Collectors.toList());
		return used_pkgs.toArray(new String[used_pkgs.size()]);
	}

	@Override public void provision() {
		IslandProvisioning.reprovision(mContext);
	}

	private void enableSystemAppForActivity(final Intent intent) {
		if (mDevicePolicies.enableSystemApp(intent))
			Log.d(TAG, "System apps enabled for: " + intent);
	}

	private void enableSystemApp(final String pkg) {
		if (! mDevicePolicies.enableSystemApp(pkg))
			Log.e(TAG, "Failed to enable: " + pkg);
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;

	private static final String TAG = IslandManagerService.class.getSimpleName();

	public static class AidlService extends Service {

		@Nullable @Override public IBinder onBind(final Intent intent) { return mStub.get(); }

		// Postpone the instantiation after service is attached for a valid context.
		private final Supplier<IslandManagerService> mStub = () -> new IslandManagerService(this);
	}
}
