package com.oasisfeng.island.installer

import android.app.Notification
import android.app.Notification.BigTextStyle
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import com.oasisfeng.android.base.Versions
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.island.installer.AppInstallerUtils.hasRequestedLegacyExternalStorage
import com.oasisfeng.island.notification.NotificationIds
import com.oasisfeng.island.util.Users


/**
 * Show helper notification about newly installed app.
 *
 * Created by Oasis on 2018-11-16.
 */
internal object AppInstallationNotifier {

	@JvmStatic fun onInstallStart(context: Context, sessionId: Int, install: AppInstallInfo) {
		val title: String = makeProcedureText(context, install, completed = false)

		showNotification(context, sessionId, title) {
			setSubText(install.callerLabel).setGroup(install.caller).setContentText(install.appId).setShowWhen(true) }
	}

	@JvmStatic fun onPackageInfoReady(context: Context, sessionId: Int, install: AppInstallInfo, current: PackageInfo?): CharSequence? {
		val details = StringBuilder()
		if (install.details == null) {      // Details may be pre-filled for split APK.
			// Line 1: Version
			install.versionName?.also {
				if (current == null) details.append(context.getString(R.string.notification_app_version, it))
				else details.append(context.getString(R.string.notification_app_version_update, current.versionName, it))
				details.append('\n') }
			// Line 2: Target SDK version （with storage mode if permission READ_EXTERNAL_STORAGE is requested)
			fun makeTargetAndroidVersionWithStorageState(targetSdkVersion: Int, requestedLegacyExternalStorage: Boolean): String =
					if (targetSdkVersion == Q && requestedLegacyExternalStorage)
						context.getString(R.string.notification_app_target_android_10_legacy_storage)
					else Versions.getAndroidVersionNumber(targetSdkVersion)
			install.targetSdkVersion?.also { targetSdk ->
				val target: String = makeTargetAndroidVersionWithStorageState(targetSdk, install.requestedLegacyExternalStorage)
				val currentTarget = current?.applicationInfo?.run {
					makeTargetAndroidVersionWithStorageState(targetSdkVersion, hasRequestedLegacyExternalStorage()) }
				if (currentTarget != null && target != currentTarget)
					details.append(context.getString(R.string.notification_app_target_android_version_update, currentTarget, target))
				else details.append(context.getString(R.string.notification_app_target_android_version, target)) }
			// Line 3: App ID (only if "development settings" is enabled in system Settings)
			if (Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1)
				details.append("\n\n").append(install.appId)
			// TODO: Changes to sensitive permissions?
		} else details.append(install.details)

		return details.takeIf { it.isNotEmpty() }?.toString().also {
			showNotification(context, sessionId, makeProcedureText(context, install, completed = false)) {
				setOnlyAlertOnce(true).setShowWhen(true).setSubText(install.appId)
						.setContentText(it).style = BigTextStyle().bigText(it) }}
	}

	@JvmStatic fun onPackageInstalled(context: Context, sessionId: Int, pkg: String, install: AppInstallInfo) {
		showAppInfoNotification(context, sessionId, pkg, makeProcedureText(context, install, completed = true),
				install.details, timeout = if (install.mode == AppInstallInfo.Mode.CLONE) 300_000 else 0)   // Timeout only for "app cloned"
	}

	fun onInstallAbort(context: Context, sessionId: Int, install: AppInstallInfo) {
		showNotification(context, sessionId, context.getText(R.string.prompt_install_aborted)) {
			setOnlyAlertOnce(true).setShowWhen(true).setContentText(makeProcedureText(context, install, completed = false)) } // No alert as abort is usually caused by user refusal.
	}

	fun onInstallFail(context: Context, sessionId: Int, install: AppInstallInfo, message: String) {
		showNotification(context, sessionId, context.getString(R.string.dialog_install_failure_title, install.appLabel)) {
			setShowWhen(true).setContentText(message).style = BigTextStyle().bigText(message) }   // TODO: Add action for fallback
	}

	@JvmStatic fun cancel(context: Context, sessionId: Int) = NotificationIds.AppInstallation.cancel(context, sessionId.toString())

	private fun makeProcedureText(context: Context, install: AppInstallInfo, completed: Boolean) = context.getString(when (install.mode) {
		AppInstallInfo.Mode.CLONE ->
			if (! completed) R.string.progress_dialog_cloning else R.string.notification_caller_cloned_app
		AppInstallInfo.Mode.UPDATE ->
			if (! completed) R.string.progress_dialog_updating
			else if (install.appId == install.caller) R.string.notification_caller_updated_self
			else R.string.notification_caller_updated_app
		AppInstallInfo.Mode.INHERIT ->
			if (! completed) R.string.progress_dialog_expanding else R.string.notification_caller_expanded_app
		else -> if (! completed) R.string.progress_dialog_installing else R.string.notification_caller_installed_app
	}, install.callerLabel, install.appLabel ?: context.getString(R.string.label_unknown_app))

	@JvmStatic fun showNotification(context: Context, sessionId: Int, title: CharSequence?, decorate: Notification.Builder.() -> Unit = {}) {
		val n = @Suppress("DEPRECATION") Notification.Builder(context).setContentTitle(title)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getColor(R.color.primary))
		NotificationIds.AppInstallation.post(context, sessionId.toString(), n.apply(decorate))
	}

	fun showAppInfoNotification(context: Context, sessionId: Int, pkg: String, title: CharSequence,
	                            text: CharSequence? = null, bigText: CharSequence? = text, timeout: Long = 0) {
		showNotification(context, sessionId, title) {
			text?.also { setContentText(it) }; bigText?.also { style = BigTextStyle().bigText(it) }
			if (SDK_INT >= O) setTimeoutAfter(timeout) else {
				val appContext = context.applicationContext
				Handler(Looper.getMainLooper()).run {
					val token = "$TAG:$pkg"
					removeCallbacksAndMessages(token)
					postAtTime({ NotificationIds.AppInstallation.cancel(appContext, pkg) },
							token, SystemClock.uptimeMillis() + timeout) }}
			addAppInfoAction(context, this, pkg, Users.current()) }
	}

	private fun addAppInfoAction(context: Context, n: Notification.Builder, pkg: String, user: UserHandle) {
		val forwarder = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setClass(context, AppInfoForwarderActivity::class.java)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, pkg).putExtra(Intent.EXTRA_USER, user)
		AppInfoForwarderActivity.markAsLaunchedBySettings(forwarder)    // Otherwise app settings is launched instead.
		val action = PendingIntent.getActivity(context, 0, forwarder, PendingIntent.FLAG_UPDATE_CURRENT)
		@Suppress("DEPRECATION") n.setContentIntent(action).addAction(R.drawable.ic_settings_applications_white_24dp,
				context.getString(R.string.action_show_app_settings), action)
	}

	private const val TAG = "Island.AIN"
}
