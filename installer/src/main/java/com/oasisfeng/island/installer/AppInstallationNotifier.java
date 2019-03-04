package com.oasisfeng.island.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.oasisfeng.android.base.Versions;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.notification.NotificationIds;

import java.util.ArrayList;
import java.util.List;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.EXTRA_USER;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.os.Build.VERSION_CODES.M;
import static com.oasisfeng.android.content.IntentCompat.ACTION_SHOW_APP_INFO;

/**
 * Show notification about newly installed app.
 *
 * Created by Oasis on 2018-11-16.
 */
class AppInstallationNotifier {

	private static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";		// Intent.EXTRA_PACKAGE_NAME

	static void onPackageInstalled(final Context context, final String caller_pkg, final CharSequence caller_app_label, final String pkg) {
		final PackageManager pm = context.getPackageManager();
		PackageInfo installed_pkg_info = null;
		if (pkg != null) try {
			installed_pkg_info = pm.getPackageInfo(pkg, GET_UNINSTALLED_PACKAGES | GET_PERMISSIONS);
		} catch (final PackageManager.NameNotFoundException ignored) {}
		if (installed_pkg_info == null) {
			Log.e(TAG, "Unknown installed package: " + pkg);
			return;
		}
		String text = null, big_text = null;
		final int target_api = installed_pkg_info.applicationInfo.targetSdkVersion;
		if (target_api < M) {
			final List<CharSequence> dangerous_permissions = new ArrayList<>();
			for (final String requested_permission : installed_pkg_info.requestedPermissions) try {
				final PermissionInfo permission_info = pm.getPermissionInfo(requested_permission, 0);
				if ((permission_info.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0)
					dangerous_permissions.add(permission_info.loadLabel(pm));
			} catch (final PackageManager.NameNotFoundException ignored) {}
			if (! dangerous_permissions.isEmpty())
				text = big_text = context.getString(R.string.notification_app_with_permissions, TextUtils.join(", ", dangerous_permissions));
		}
		if (text == null) text = context.getString(target_api >= M ? R.string.notification_app_target_api : R.string.notification_app_target_pre_m,
				Versions.getAndroidVersionNumber(target_api));

		final Intent app_info_intent = new Intent(ACTION_SHOW_APP_INFO).putExtra(EXTRA_PACKAGE_NAME, pkg).putExtra(EXTRA_USER, Process.myUserHandle());
		final PendingIntent app_settings = PendingIntent.getActivity(context, 0, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
				Uri.fromParts("package", pkg, null)), FLAG_UPDATE_CURRENT);
		final boolean is_update = installed_pkg_info.lastUpdateTime != installed_pkg_info.firstInstallTime;
		NotificationIds.AppInstallation.post(context, pkg, new Notification.Builder(context)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getResources().getColor(R.color.accent))
				.setContentTitle(context.getString(is_update ? R.string.notification_caller_updated_app : R.string.notification_caller_installed_app,
						caller_app_label, pkg.equals(caller_pkg) ? caller_app_label : Apps.of(context).getAppName(pkg)))
				.setContentText(text).setStyle(big_text == null ? null : new Notification.BigTextStyle().bigText(big_text))
				.setContentIntent(app_settings).addAction(R.drawable.ic_settings_applications_white_24dp, context.getString(R.string.action_show_app_settings), app_settings)
				.addAction(pm.resolveActivity(app_info_intent, 0) == null ? null
						: new Notification.Action(R.drawable.ic_settings_applications_white_24dp, context.getString(R.string.action_show_app_info),
						PendingIntent.getActivity(context, pkg.hashCode()/* avoid overwriting */, app_info_intent, FLAG_UPDATE_CURRENT))));
	}

	private static final String TAG = "Island.AIN";
}
