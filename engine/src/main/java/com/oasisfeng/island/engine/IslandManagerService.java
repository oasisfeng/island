package com.oasisfeng.island.engine;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.IBinder;
import android.os.StrictMode;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.widget.Toasts;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

import java.io.File;

import java9.util.function.Supplier;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.island.engine.IslandManager.CLONE_RESULT_OK_INSTALL;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManagerService extends IIslandManager.Stub {

	private static final String SCHEME_PACKAGE = "package";

	public IslandManagerService(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
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

	@Override public String launchApp(final String pkg) {
		if (! Users.isOwner() || new DevicePolicies(mContext).isActiveDeviceOwner()) {
			if (mDevicePolicies.isApplicationHidden(pkg)) {		// Hidden or not installed
				if (! mDevicePolicies.setApplicationHidden(pkg, false))
					if (! Apps.of(mContext).isInstalledInCurrentUser(pkg)) return "not_installed";	// Not installed in profile, just give up.
			}
			if (SDK_INT >= N) try {
				if (mDevicePolicies.isPackageSuspended(pkg))
					mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
			} catch (final PackageManager.NameNotFoundException ignored) { return "not_found"; }
		}

		final Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(pkg);
		if (intent == null) return "no_launcher_activity";
		intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			mContext.startActivity(intent);
		} catch (final ActivityNotFoundException e) {
			return "launcher_activity_not_found";
		}
		return null;
	}

	@Override public int cloneUserApp(final String pkg, final String apk_path, final boolean do_it) {
		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestrictionsIfNeeded(mContext, UserManager.DISALLOW_INSTALL_APPS);

		if (! IslandProvisioning.ensureInstallNonMarketAppAllowed(mContext, mDevicePolicies))
			return cloneUserAppViaMarketApp(pkg, do_it);

		final String my_pkg = mContext.getPackageName();
		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts(SCHEME_PACKAGE, pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, my_pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (SDK_INT >= O) intent.setData(Uri.fromFile(new File(apk_path)));
		enableSystemAppForActivity(intent);				// Ensure package installer is enabled.

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
		enableSystemAppForActivity(market_intent);
		final ResolveInfo market_info = mContext.getPackageManager().resolveActivity(market_intent, SDK_INT < N ? 0 : PackageManager.MATCH_SYSTEM_ONLY);
		if (market_info == null || (market_info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) == 0)	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
			return IslandManager.CLONE_RESULT_NO_SYS_MARKET;

		if (WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.activityInfo.applicationInfo.packageName)) {
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

	@RequiresApi(N) @Override public boolean block(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, true);
		return failed == null || failed.length == 0;
	}

	@RequiresApi(N) @Override public boolean unblock(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		return failed == null || failed.length == 0;
	}

	@Override public void provision() {
		IslandProvisioning.reprovision(mContext);
	}

	private void enableSystemAppForActivity(final Intent intent) {
		if (mDevicePolicies.enableSystemApp(intent))
			Log.d(TAG, "System apps enabled for: " + intent);
	}

	@Override public boolean enableSystemApp(final String pkg) {
		return mDevicePolicies.enableSystemApp(pkg);
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
