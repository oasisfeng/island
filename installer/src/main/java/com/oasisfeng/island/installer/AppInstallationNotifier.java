package com.oasisfeng.island.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.oasisfeng.android.base.Versions;
import com.oasisfeng.android.content.IntentCompat;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.notification.NotificationIds;

import java.util.ArrayList;
import java.util.List;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.EXTRA_USER;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Show helper notification about newly installed app.
 *
 * Created by Oasis on 2018-11-16.
 */
class AppInstallationNotifier {

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
		final boolean is_update = installed_pkg_info.lastUpdateTime != installed_pkg_info.firstInstallTime, is_self_update = pkg.equals(caller_pkg);
		final String title = context.getString(! is_update ? R.string.notification_caller_installed_app
						: is_self_update ? R.string.notification_caller_updated_self : R.string.notification_caller_updated_app,
				caller_app_label, is_self_update ? null/* unused */ : Apps.of(context).getAppName(pkg));

		final int target_api = installed_pkg_info.applicationInfo.targetSdkVersion;
		List<CharSequence> dangerous_permissions = null;
		if (! is_update && SDK_INT >= M && target_api < M && installed_pkg_info.requestedPermissions != null) {
			dangerous_permissions = new ArrayList<>();
			for (final String requested_permission : installed_pkg_info.requestedPermissions) try {
				final PermissionInfo permission_info = pm.getPermissionInfo(requested_permission, 0);
				if ((permission_info.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0)
					dangerous_permissions.add(permission_info.loadLabel(pm));
			} catch (final PackageManager.NameNotFoundException ignored) {}
		}
		String big_text = null;
		final String target_version = Versions.getAndroidVersionNumber(target_api);
		final String text = dangerous_permissions == null ? context.getString(R.string.notification_app_target_api, target_version)
				: dangerous_permissions.isEmpty() ? context.getString(R.string.notification_app_target_pre_m_wo_sensitive_permissions, target_version)
				: (big_text = context.getString(R.string.notification_app_with_permissions, TextUtils.join(", ", dangerous_permissions)));

		final Intent app_info_forwarder = new Intent(IntentCompat.ACTION_SHOW_APP_INFO).setClass(context, AppInfoForwarderActivity.class)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, pkg).putExtra(EXTRA_USER, Process.myUserHandle());
		final PendingIntent app_info = PendingIntent.getActivity(context, 0, app_info_forwarder, FLAG_UPDATE_CURRENT);
		NotificationIds.AppInstallation.post(context, pkg, new Notification.Builder(context)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getResources().getColor(R.color.accent))
				.setContentTitle(title).setContentText(text).setStyle(big_text != null ? new Notification.BigTextStyle().bigText(big_text) : null)
				.setContentIntent(app_info).addAction(R.drawable.ic_settings_applications_white_24dp, context.getString(R.string.action_show_app_settings), app_info));
	}

	private static final String TAG = "Island.AIN";
}
