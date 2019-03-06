package com.oasisfeng.island.watcher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.api.Api;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import androidx.annotation.RequiresApi;
import java9.util.function.Consumer;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
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
	private static final String EXTRA_WATCHING_PERMISSIONS = "permissions";

	@Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData(); final String pkg = data != null ? data.getSchemeSpecificPart() : null;
		if (pkg == null) return;
		final String action = intent.getAction();
		if (action != null) switch (action) {
		case ACTION_REFREEZE:
			refreeze(context, pkg, intent.getStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS));
			break;
		case ACTION_DISMISS:
		case Intent.ACTION_PACKAGE_REMOVED:
		case Intent.ACTION_PACKAGE_FULLY_REMOVED:	// Declared in AndroidManifest
			NotificationIds.IslandAppWatcher.cancel(context, pkg);
			break;
		case DevicePolicies.ACTION_PACKAGE_UNFROZEN:
			if (NotificationIds.IslandAppWatcher.isBlocked(context)) return;
			try {
				final PackageInfo info = context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
				Log.i(TAG, "App is available: " + pkg);
				startWatching(context, info);
			} catch (final PackageManager.NameNotFoundException e) {
				Log.w(TAG, "App is unavailable: " + pkg);
				NotificationIds.IslandAppWatcher.cancel(context, pkg);
			}
			break;
		}
	}

	private void refreeze(final Context context, final String pkg, final ArrayList<String> watching_permissions) {
		if (mCallerId == null) mCallerId = PendingIntent.getBroadcast(context, 0, new Intent(), FLAG_UPDATE_CURRENT);
		context.sendBroadcast(new Intent(Api.latest.ACTION_FREEZE, Uri.fromParts("package", pkg, null))
				.putExtra(Api.latest.EXTRA_CALLER_ID, mCallerId).setPackage(context.getPackageName()));
	}

	private static void startWatching(final Context context, final PackageInfo info) {
		final ArrayList<String> watching_permissions = new ArrayList<>();
		for (int i = 0; i < info.requestedPermissions.length; i++)
			if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
				final String permission = info.requestedPermissions[i];
				if (CONCERNED_PERMISSIONS.contains(permission)) watching_permissions.add(permission);
			}
		final CharSequence app_label = info.applicationInfo.loadLabel(context.getPackageManager());
		NotificationIds.IslandAppWatcher.post(context, info.packageName, new Notification.Builder(context).setOngoing(true).setGroup(IslandWatcher.GROUP)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getResources().getColor(R.color.primary))
				.setContentTitle(app_label + " is active").setContentText("Pending re-freeze")
				.addAction(new Notification.Action(0, "Freeze", makePendingIntent(context, ACTION_REFREEZE, info.packageName,
						intent -> intent.putStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS, watching_permissions))))
				.addAction(new Notification.Action(0, "Settings", PendingIntent.getActivity(context, 0,
						NotificationIds.IslandWatcher.buildChannelSettingsIntent(context), FLAG_UPDATE_CURRENT)))
				.addAction(new Notification.Action(0, "Dismiss", makePendingIntent(context, ACTION_DISMISS, info.packageName, null))));
	}

	private static PendingIntent makePendingIntent(final Context context, final String action, final String pkg, final Consumer<Intent> extras) {
		final Intent intent = new Intent(action).setClass(context, IslandAppWatcher.class);
		if (pkg != null) intent.setData(Uri.fromParts("package", pkg, null));
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
