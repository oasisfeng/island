package com.oasisfeng.island;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.PatternMatcher;
import android.os.SystemClock;
import android.os.UserHandle;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.android.os.Loopers;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.shuttle.ContextShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.app.PendingIntent.getBroadcast;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Prompt user for full removal if Island is uninstalled in owner user without removing managed profile first.
 *
 * Created by Oasis on 2017/9/14.
 */
public class UninstallHelper extends PseudoContentProvider {

	private static final String ACTION_DESTROY_ISLAND = "DESTROY_ISLAND";
	private static final String ACTION_RESTORE_ISLAND = "RESTORE_ISLAND";
	private static final String ACTION_RECHECK_ISLAND = "RECHECK_ISLAND";

	@Override public boolean onCreate() {
		if (! Users.isOwner()) Loopers.addIdleTask(this::checkAndMonitor);		// Do nothing in owner user.
		return false;
	}

	private void checkAndMonitor() {
		check(context());

		if (SDK_INT >= O) return;		// No longer works on Android O+
		final LauncherApps launcher = (LauncherApps) context().getSystemService(Context.LAUNCHER_APPS_SERVICE);
		if (launcher != null) launcher.registerCallback(new LauncherApps.Callback() {
			@Override public void onPackageRemoved(final String pkg, final UserHandle user) {
				if (context().getPackageName().equals(pkg) && Users.isOwner(user)) onIslandRemovedInOwnerUser(context());
			}
			@Override public void onPackageAdded(final String packageName, final UserHandle user) {}
			@Override public void onPackageChanged(final String packageName, final UserHandle user) {}
			@Override public void onPackagesAvailable(final String[] packageNames, final UserHandle user, final boolean replacing) {}
			@Override public void onPackagesUnavailable(final String[] packageNames, final UserHandle user, final boolean replacing) {}
		});
	}

	private static void check(final Context context) {
		final PackageManager owner_pm;
		if (Permissions.has(context, INTERACT_ACROSS_USERS) && (owner_pm = ContextShuttle.getPackageManagerAsUser(context, Users.owner)) != null) try { @SuppressLint("WrongConstant")
			final ApplicationInfo owner_island = owner_pm.getApplicationInfo(context.getPackageName(), Hacks.GET_ANY_USER_AND_UNINSTALLED);
			if ((owner_island.flags & ApplicationInfo.FLAG_INSTALLED) == 0)
				onIslandRemovedInOwnerUser(context);
			return;
		} catch (final PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Island not found");	// Should not happen
		}

		final File app_mainland_data_path = new File(new File(Environment.getDataDirectory(), "data"), context.getPackageName());
		try {
			final StructStat stat = Os.stat(app_mainland_data_path.getAbsolutePath());
			if (stat != null) context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, UninstallHelper.class),
					COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);	// Mark as enabled if path is compatible.
		} catch (final ErrnoException e) {
			if (e.errno != OsConstants.ENOENT) {
				Analytics.$().report(e);
				return;
			}
			if (COMPONENT_ENABLED_STATE_ENABLED == context.getPackageManager().getComponentEnabledSetting(new ComponentName(context, UninstallHelper.class))) {
				onIslandRemovedInOwnerUser(context);		// Only if the path compatibility is confirmed when it was still installed.
			} else Log.w(TAG, "Uninstallation state in mainland cannot be detected due to incompatibility");
		}
	}

	private static void onIslandRemovedInOwnerUser(final Context context) {
		final Notification.Action action_destroy = new Notification.Action.Builder(R.drawable.ic_delete_forever_white_24dp, context.getString(R.string.dialog_button_destroy),
				getBroadcast(context, 0, new Intent(context, UninstallReceiver.class).setAction(ACTION_DESTROY_ISLAND), FLAG_UPDATE_CURRENT)).build();
		final Notification.Builder n = new Notification.Builder(context).setOngoing(true).setSmallIcon(android.R.drawable.stat_sys_warning)
				.setContentTitle(context.getString(R.string.notification_island_after_uninstall_title))
				.setContentText(context.getString(R.string.notification_island_after_uninstall_text))
				.addAction(action_destroy).addAction(new Notification.Action.Builder(R.drawable.ic_undo_white_24dp, context.getString(R.string.notification_action_restore),
				getBroadcast(context, 0, new Intent(context, UninstallReceiver.class).setAction(ACTION_RESTORE_ISLAND), FLAG_UPDATE_CURRENT)).build());
		NotificationIds.UninstallHelper.post(context, n);
	}

	private static void openIslandWithinAppMarketInOwnerUser(final Context context) {
		startActivityByCrossProfileIntentFilter(context, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName())));
	}

	private static void reinstallInOwnerUserWithPackageInstaller(final Context context) {
		startActivityByCrossProfileIntentFilter(context, new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.parse("package:" + context.getPackageName())));
	}

	private static boolean startActivityByCrossProfileIntentFilter(final Context context, final Intent intent) {
		final Uri uri = Objects.requireNonNull(intent.getData());
		new DevicePolicies(context).addCrossProfileIntentFilter(IntentFilters.forAction(intent.getAction())
				.withData(uri.getScheme(), uri.getSchemeSpecificPart(), PatternMatcher.PATTERN_LITERAL), FLAG_PARENT_CAN_ACCESS_MANAGED);
		final List<ResolveInfo> resolves = context.getPackageManager().queryIntentActivities(intent, 0);
		for (final ResolveInfo resolve : resolves) if ("android".equals(resolve.activityInfo.packageName)) {		// IntentForwarder
			context.startActivity(intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)));
			return true;
		}
		return false;
	}

	public static class UninstallReceiver extends BroadcastReceiver {

		@Override public void onReceive(final Context context, final Intent intent) {
			if (Users.isOwner()) return;
			final String action = intent.getAction();
			if (ACTION_RECHECK_ISLAND.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
				check(context);
			} else if (ACTION_DESTROY_ISLAND.equals(action)) {
				final DevicePolicyManager dpm = Objects.requireNonNull((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE));
				dpm.wipeData(0);
				// No need to cancel the notification as it will be canceled after managed profile is removed.
			} else if (ACTION_RESTORE_ISLAND.equals(action)) {
				NotificationIds.UninstallHelper.cancel(context);		// Cancel the notification and re-check later in minutes.
				final AlarmManager am = Objects.requireNonNull((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
				final long now = SystemClock.elapsedRealtime();
				final Intent recheck = new Intent(context, getClass()).setAction(ACTION_RECHECK_ISLAND);
				am.set(AlarmManager.ELAPSED_REALTIME, now + 180_000, getBroadcast(context, 0, recheck, FLAG_UPDATE_CURRENT));

				// There's a bug in Android O which prevents Package Installer from correctly identifying the target SDK version of calling package
				//   not installed in the same user as Package Installer. Unfortunately that is our case.
				if (SDK_INT >= O) openIslandWithinAppMarketInOwnerUser(context);
				else reinstallInOwnerUserWithPackageInstaller(context);
			}
		}
	}

	private static final String TAG = "Island.UH";
}
