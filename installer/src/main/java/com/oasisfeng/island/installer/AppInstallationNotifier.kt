package com.oasisfeng.island.installer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.BigTextStyle
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.O
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import com.oasisfeng.android.base.Versions
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.installer.AppInfoForwarderActivity.CALLER_PLACEHOLDER_FOR_SETTINGS
import com.oasisfeng.island.notification.NotificationIds
import java.util.*


/**
 * Show helper notification about newly installed app.
 *
 * Created by Oasis on 2018-11-16.
 */
internal object AppInstallationNotifier {

	@JvmStatic fun onPackageInstalled(context: Context, callerPkg: String, callerAppLabel: CharSequence?, pkg: String, user: UserHandle) {
		val pm = context.packageManager
		@SuppressLint("InlinedApi") val info = (try {
			pm.getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_PERMISSIONS)
		} catch (e: PackageManager.NameNotFoundException) { null }) ?: return Unit.also { Log.e(TAG, "Unknown installed package: $pkg") }
		val isUpdate = info.lastUpdateTime != info.firstInstallTime; val isSelfUpdate = pkg == callerPkg

		val titleRes = if (! isUpdate) R.string.notification_caller_installed_app else if (isSelfUpdate) R.string.notification_caller_updated_self else R.string.notification_caller_updated_app
		val title = context.getString(titleRes, callerAppLabel, if (isSelfUpdate) null /* unused */ else Apps.of(context).getAppName(pkg))
		val targetApi = info.applicationInfo.targetSdkVersion
		var dangerousPermissions: MutableList<CharSequence?>? = null
		if (! isUpdate && targetApi < M && info.requestedPermissions != null) {
			dangerousPermissions = ArrayList()
			for (requested_permission in info.requestedPermissions) try {
				val permissionInfo = pm.getPermissionInfo(requested_permission, 0)
				@Suppress("DEPRECATION") if (permissionInfo.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS != 0)
					dangerousPermissions.add(permissionInfo.loadLabel(pm))
			} catch (ignored: PackageManager.NameNotFoundException) {}
		}
		var bigText: String? = null
		val targetVersion = Versions.getAndroidVersionNumber(targetApi)
		val text = when {
			dangerousPermissions == null -> context.getString(R.string.notification_app_target_api, targetVersion)
			dangerousPermissions.isEmpty() -> context.getString(R.string.notification_app_target_pre_m_wo_sensitive_permissions, targetVersion)
			else -> context.getString(R.string.notification_app_with_permissions, TextUtils.join(", ", dangerousPermissions)).also { bigText = it }}

		showNotification(context, pkg, user, title, text, bigText)
	}

	fun showNotification(context: Context, pkg: String, user: UserHandle, title: CharSequence,
	                             text: CharSequence? = null, bigText: CharSequence? = null, timeout: Long = 0) {
		@Suppress("DEPRECATION") val n: Notification.Builder = Notification.Builder(context)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.resources.getColor(R.color.accent))
				.setContentTitle(title).apply { text?.also { setContentText(it) }; bigText?.also { style = BigTextStyle().bigText(it) }}
		if (SDK_INT >= O) n.setTimeoutAfter(timeout) else {
			val appContext = context.applicationContext
			Handler(Looper.getMainLooper()).run {
				val token = "$TAG:$pkg"
				removeCallbacksAndMessages(token)
				postAtTime({ NotificationIds.AppInstallation.cancel(appContext, pkg) },
						token, SystemClock.uptimeMillis() + timeout) }}
		NotificationIds.AppInstallation.post(context, pkg, addAppInfoAction(context, n, pkg, user))
	}

	private fun addAppInfoAction(context: Context, n: Notification.Builder, pkg: String, user: UserHandle): Notification.Builder {
		val forwarder = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setClass(context, AppInfoForwarderActivity::class.java)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, pkg).putExtra(Intent.EXTRA_USER, user)
				.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$CALLER_PLACEHOLDER_FOR_SETTINGS"))  // Otherwise app settings is launched instead.
		val action = PendingIntent.getActivity(context, 0, forwarder, PendingIntent.FLAG_UPDATE_CURRENT)
		@Suppress("DEPRECATION") return n.setContentIntent(action).addAction(R.drawable.ic_settings_applications_white_24dp,
				context.getString(R.string.action_show_app_settings), action)
	}

	private const val TAG = "Island.AIN"
}
