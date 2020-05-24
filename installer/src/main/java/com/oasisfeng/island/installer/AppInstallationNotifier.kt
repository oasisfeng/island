package com.oasisfeng.island.installer

import android.app.Notification
import android.app.Notification.BigTextStyle
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Process
import android.text.TextUtils
import android.util.Log
import com.oasisfeng.android.base.Versions
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.notification.NotificationIds
import java.util.*

/**
 * Show helper notification about newly installed app.
 *
 * Created by Oasis on 2018-11-16.
 */
internal object AppInstallationNotifier {

	@JvmStatic fun onPackageInstalled(context: Context, caller_pkg: String, caller_app_label: CharSequence?, pkg: String?) {
		val pm = context.packageManager
		val installedPackageInfo: PackageInfo? = if (pkg != null) try {
			pm.getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_PERMISSIONS)
		} catch (ignored: PackageManager.NameNotFoundException) { null } else null
		if (installedPackageInfo == null) {
			Log.e(TAG, "Unknown installed package: $pkg")
			return
		}
		val isUpdate = installedPackageInfo.lastUpdateTime != installedPackageInfo.firstInstallTime
		val isSelfUpdate = pkg == caller_pkg
		val titleRes = if (! isUpdate) R.string.notification_caller_installed_app else if (isSelfUpdate) R.string.notification_caller_updated_self else R.string.notification_caller_updated_app
		val title = context.getString(titleRes, caller_app_label, if (isSelfUpdate) null /* unused */ else Apps.of(context).getAppName(pkg))
		val targetApi = installedPackageInfo.applicationInfo.targetSdkVersion
		var dangerousPermissions: MutableList<CharSequence?>? = null
		if (! isUpdate && SDK_INT >= M && targetApi < M && installedPackageInfo.requestedPermissions != null) {
			dangerousPermissions = ArrayList()
			for (requested_permission in installedPackageInfo.requestedPermissions) try {
				val permissionInfo = pm.getPermissionInfo(requested_permission, 0)
				if (permissionInfo.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS != 0)
					dangerousPermissions.add(permissionInfo.loadLabel(pm))
			} catch (ignored: PackageManager.NameNotFoundException) {}
		}
		var bigText: String? = null
		val targetVersion = Versions.getAndroidVersionNumber(targetApi)
		val text = when {
			dangerousPermissions == null -> context.getString(R.string.notification_app_target_api, targetVersion)
			dangerousPermissions.isEmpty() -> context.getString(R.string.notification_app_target_pre_m_wo_sensitive_permissions, targetVersion)
			else -> context.getString(R.string.notification_app_with_permissions, TextUtils.join(", ", dangerousPermissions)).also { bigText = it }}
		val appInfoForwarder = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setClass(context, AppInfoForwarderActivity::class.java)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, pkg).putExtra(Intent.EXTRA_USER, Process.myUserHandle())
		val appInfo = PendingIntent.getActivity(context, 0, appInfoForwarder, PendingIntent.FLAG_UPDATE_CURRENT)
		@Suppress("DEPRECATION") NotificationIds.AppInstallation.post(context, pkg, Notification.Builder(context)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.resources.getColor(R.color.accent))
				.setContentTitle(title).setContentText(text).setStyle(if (bigText != null) BigTextStyle().bigText(bigText) else null)
				.setContentIntent(appInfo).addAction(R.drawable.ic_settings_applications_white_24dp, context.getString(R.string.action_show_app_settings), appInfo))
	}

	private const val TAG = "Island.AIN"
}