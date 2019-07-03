package com.oasisfeng.island.watcher;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.api.Api;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java9.util.function.Consumer;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.app.Notification.CATEGORY_STATUS;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.O;

/**
 * App watcher for unfrozen apps in Island, for convenient refreezing.
 *
 * Created by Oasis on 2019-2-27.
 */
@RequiresApi(O) public class IslandAppWatcher extends BroadcastReceiver {

	private static final Collection<String> CONCERNED_PERMISSIONS = Arrays.asList(
			CAMERA, RECORD_AUDIO, READ_SMS, RECEIVE_SMS, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION);

	private static final String ACTION_REFREEZE = "REFREEZE";
	private static final String ACTION_DISMISS = "DISMISS";
	private static final String ACTION_REVOKE_PERMISSION = "REVOKE_PERMISSION";
	private static final String EXTRA_WATCHING_PERMISSIONS = "permissions";		// ArrayList<String>

	@Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData(); final String ssp = data != null ? data.getSchemeSpecificPart() : null;
		if (ssp == null) return;
		final String action = intent.getAction();
		if (action != null) switch (action) {
		case ACTION_REFREEZE:
			refreeze(context, ssp, intent.getStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS));
			break;
		case ACTION_DISMISS:
			NotificationIds.IslandAppWatcher.cancel(context, "package".equals(data.getScheme()) ? ssp : data.toString());
			break;
		case Intent.ACTION_PACKAGE_REMOVED:
		case Intent.ACTION_PACKAGE_FULLY_REMOVED:	// Declared in AndroidManifest
			NotificationIds.IslandAppWatcher.cancel(context, ssp);
			break;
		case DevicePolicies.ACTION_PACKAGE_UNFROZEN:
			if (NotificationIds.IslandAppWatcher.isBlocked(context)) return;
			try {
				final PackageInfo info = context.getPackageManager().getPackageInfo(ssp, PackageManager.GET_PERMISSIONS);
				Log.i(TAG, "App is available: " + ssp);
				startWatching(context, info);
			} catch (final PackageManager.NameNotFoundException e) {
				Log.w(TAG, "App is unavailable: " + ssp);
				NotificationIds.IslandAppWatcher.cancel(context, ssp);
			}
			break;
		case ACTION_REVOKE_PERMISSION:
			final String pkg = data.getScheme();
			final DevicePolicies policies = new DevicePolicies(context);
			final boolean hidden = policies.invoke(DevicePolicyManager::isApplicationHidden, pkg);
			if (hidden) policies.setApplicationHiddenWithoutAppOpsSaver(pkg, false);		// setPermissionGrantState() only works for unfrozen app
			try {
				if (policies.invoke(DevicePolicyManager::setPermissionGrantState, pkg, ssp, DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED))
					policies.invoke(DevicePolicyManager::setPermissionGrantState, pkg, ssp, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
				else Log.e(TAG, "Failed to revoke permission " + ssp + " for " + pkg);
			} finally {
				if (hidden) policies.setApplicationHiddenWithoutAppOpsSaver(pkg, true);
			}
			NotificationIds.IslandAppWatcher.cancel(context, data.toString());
			break;
		}
	}

	private void refreeze(final Context context, final String pkg, final @Nullable List<String> watching_permissions) {
		if (mCallerId == null) mCallerId = PendingIntent.getBroadcast(context, 0, new Intent(), FLAG_UPDATE_CURRENT);
		context.sendBroadcast(new Intent(Api.latest.ACTION_FREEZE, Uri.fromParts("package", pkg, null))
				.putExtra(Api.latest.EXTRA_CALLER_ID, mCallerId).setPackage(context.getPackageName()));
		if (watching_permissions == null) return;
		final PackageManager pm = context.getPackageManager();
		final String[] granted_permissions = watching_permissions.stream().filter(p -> pm.checkPermission(p, pkg) == PERMISSION_GRANTED).toArray(String[]::new);
		if (granted_permissions.length == 0) return;

		final CharSequence app_name = Apps.of(context).getAppName(pkg);
		for (final String granted_permission : granted_permissions) try {
			final CharSequence permission_label = pm.getPermissionInfo(granted_permission, 0).loadLabel(pm);
			NotificationIds.IslandAppWatcher.post(context, Uri.fromParts(pkg, granted_permission, null).toString(), new Notification.Builder(context)
					.setOngoing(true).setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getColor(R.color.accent))
					.setSubText(app_name).setCategory(CATEGORY_STATUS).setGroup(IslandWatcher.GROUP)
					.setContentTitle(context.getString(R.string.notification_permission_was_granted_title, permission_label))
					.setContentText(context.getText(R.string.notification_permission_was_granted_text))
					.addAction(new Action.Builder(null, context.getText(R.string.action_keep_granted),
							makePendingIntent(context, ACTION_DISMISS, pkg, granted_permission, null)).build())
					.addAction(new Action.Builder(null, context.getText(R.string.action_revoke_granted),
							makePendingIntent(context, ACTION_REVOKE_PERMISSION, pkg, granted_permission, null)).build()));
		} catch (final PackageManager.NameNotFoundException ignored) {}		// Should never happen
	}

	private static void startWatching(final Context context, final PackageInfo info) {
		final ArrayList<String> watching_permissions;
		if (info.requestedPermissions != null) {
			watching_permissions = new ArrayList<>();
			for (int i = 0; i < info.requestedPermissions.length; i ++)
				if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
					final String permission = info.requestedPermissions[i];
					if (CONCERNED_PERMISSIONS.contains(permission)) watching_permissions.add(permission);
				}
		} else watching_permissions = null;
		final CharSequence app_label = info.applicationInfo.loadLabel(context.getPackageManager());
		NotificationIds.IslandAppWatcher.post(context, info.packageName, new Notification.Builder(context).setOngoing(true).setGroup(IslandWatcher.GROUP)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getResources().getColor(R.color.primary))
				.setContentTitle(context.getString(R.string.notification_app_watcher_title, app_label)).setContentText(context.getText(R.string.notification_app_watcher_text))
				.addAction(new Action.Builder(null, "Freeze", makePendingIntent(context, ACTION_REFREEZE, "package", info.packageName,
						watching_permissions == null ? null : intent -> intent.putStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS, watching_permissions))).build())
				.addAction(new Action.Builder(null, "Settings", PendingIntent.getActivity(context, 0,
						NotificationIds.IslandWatcher.buildChannelSettingsIntent(context), FLAG_UPDATE_CURRENT)).build())
				.addAction(new Action.Builder(null, "Dismiss", makePendingIntent(context, ACTION_DISMISS, "package", info.packageName, null)).build()));
	}

	private static PendingIntent makePendingIntent(final Context context, final String action, final String scheme, final String ssp, final @Nullable Consumer<Intent> extras) {
		final Intent intent = new Intent(action).setClass(context, IslandAppWatcher.class);
		if (ssp != null) intent.setData(Uri.fromParts(scheme, ssp, null));
		if (extras != null) extras.accept(intent);
		return PendingIntent.getBroadcast(context, 0, intent, FLAG_UPDATE_CURRENT);
	}

	private PendingIntent mCallerId;

	/** This provider tracks all freezing and unfreezing events triggered by other modules within the default process of Island */
	public static class AppStateTracker extends PseudoContentProvider {

		@Override public boolean onCreate() {
			context().registerReceiver(new IslandAppWatcher(),
					IntentFilters.forActions(DevicePolicies.ACTION_PACKAGE_UNFROZEN, Intent.ACTION_PACKAGE_REMOVED).withDataScheme("package"));
			return false;
		}
	}

	private static final String TAG = "Island.AppWatcher";
}
