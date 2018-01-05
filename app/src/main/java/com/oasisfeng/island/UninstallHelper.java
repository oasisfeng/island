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
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.android.os.Loopers;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.shuttle.ContextShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.List;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.app.PendingIntent.getBroadcast;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
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
		if (Users.isOwner()) return false;		// Do nothing in owner user.
		Loopers.addIdleTask(new Handler(), () -> {
			final LauncherApps launcher = (LauncherApps) context().getSystemService(Context.LAUNCHER_APPS_SERVICE);
			if (launcher != null) launcher.registerCallback(new LauncherApps.Callback() {
				@Override public void onPackageRemoved(final String pkg, final UserHandle user) {		// NOT WORKING since Android O.
					if (context().getPackageName().equals(pkg) && Users.isOwner(user)) onIslandRemovedInOwnerUser(context());
				}

				@Override public void onPackageAdded(final String packageName, final UserHandle user) {}
				@Override public void onPackageChanged(final String packageName, final UserHandle user) {}
				@Override public void onPackagesAvailable(final String[] packageNames, final UserHandle user, final boolean replacing) {}
				@Override public void onPackagesUnavailable(final String[] packageNames, final UserHandle user, final boolean replacing) {}
			});
		});
		return true;
	}

	private static void onIslandRemovedInOwnerUser(final Context context) {
		final Notification.Action action_destroy = new Notification.Action.Builder(R.drawable.ic_delete_forever_white_24dp, context.getString(R.string.dialog_button_destroy),
				getBroadcast(context, 0, new Intent(context, UninstallReceiver.class).setAction(ACTION_DESTROY_ISLAND), FLAG_UPDATE_CURRENT)).build();
		final Notification.Builder n = new Notification.Builder(context).setOngoing(true).setSmallIcon(android.R.drawable.stat_sys_warning)
				.setContentTitle(context.getString(R.string.notification_remove_island_after_uninstall_title))
				.setContentText(context.getString(R.string.notification_remove_island_after_uninstall_text))
				.addAction(action_destroy);
		// There's a bug in Android O which prevents Package Installer from correctly identifying the caller package (per-app package install permission)
		// not installed in the same user as Package Installer. That unfortunately is our case.
		if (SDK_INT < O) {
			final Notification.Action action_restore = new Notification.Action.Builder(R.drawable.ic_undo_white_24dp, context.getString(R.string.notification_action_restore),
					getBroadcast(context, 0, new Intent(context, UninstallReceiver.class).setAction(ACTION_RESTORE_ISLAND), FLAG_UPDATE_CURRENT)).build();
			n.addAction(action_restore);
		}
		NotificationIds.UninstallHelper.post(context, n);
	}

	public static class UninstallReceiver extends BroadcastReceiver {

		@Override public void onReceive(final Context context, final Intent intent) {
			if (Users.isOwner()) return;
			final String action = intent.getAction();
			if (ACTION_RECHECK_ISLAND.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
				if (! Permissions.has(context, INTERACT_ACROSS_USERS)) return;		// TODO: Android 5.x without INTERACT_ACROSS_USERS?
				final PackageManager owner_pm = ContextShuttle.getPackageManagerAsUser(context, Users.owner);
				if (owner_pm == null) return;
				try { @SuppressLint("WrongConstant")
					final ApplicationInfo owner_island = owner_pm.getApplicationInfo(context.getPackageName(), Hacks.MATCH_ANY_USER_AND_UNINSTALLED);
					if ((owner_island.flags & ApplicationInfo.FLAG_INSTALLED) == 0)
						onIslandRemovedInOwnerUser(context);
				} catch (final PackageManager.NameNotFoundException e) {
					Log.e(TAG, "Island not found");
				}
			} else if (ACTION_DESTROY_ISLAND.equals(action)) {
				final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
				dpm.wipeData(0);
				// No need to cancel the notification as it will be canceled after managed profile is removed.
			} else if (ACTION_RESTORE_ISLAND.equals(action)) {
				NotificationIds.UninstallHelper.cancel(context);		// Cancel the notification and re-check later in minutes.
				final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				final long now = SystemClock.elapsedRealtime();
				final Intent recheck = new Intent(context, getClass()).setAction(ACTION_RECHECK_ISLAND);
				am.set(AlarmManager.ELAPSED_REALTIME, now + 180_000, getBroadcast(context, 0, recheck, FLAG_UPDATE_CURRENT));

				final Intent install_intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", context.getPackageName(), null));
				new DevicePolicies(context).addCrossProfileIntentFilter
						(IntentFilters.forAction(install_intent.getAction()).withDataScheme(install_intent.getScheme()), FLAG_PARENT_CAN_ACCESS_MANAGED);
				@SuppressLint("WrongConstant") final List<ResolveInfo> resolves
						= context.getPackageManager().queryIntentActivities(install_intent, Hacks.MATCH_ANY_USER_AND_UNINSTALLED);
				for (final ResolveInfo resolve : resolves)
					if ("android".equals(resolve.activityInfo.packageName)) {		// IntentForwarder
						context.startActivity(install_intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)));
						return;
					}
			}
		}
	}

	private static final String TAG = "Uninstall";
}
