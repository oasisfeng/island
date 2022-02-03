package com.oasisfeng.island.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.util.Log
import com.oasisfeng.island.analytics.Analytics
import com.oasisfeng.island.util.RomVariants.isMiui

class AppInstallerStatusReceiver: BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent?) {
		val status = (intent ?: return).getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
		val legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, INSTALL_FAILED_INTERNAL_ERROR)
		val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, INVALID_SESSION_ID)
		val install = intent.getParcelableExtra<Bundle>(EXTRA_INSTALL_INFO)?.getParcelable<AppInstallInfo>(null) ?: return
		install.context = context
		if (BuildConfig.DEBUG) Log.i(TAG, "Status received: " + intent.toUri(Intent.URI_INTENT_SCHEME))

		val installer = context.packageManager.packageInstaller
		val sessionInfo = installer.getSessionInfo(sessionId)
		val pkg = sessionInfo?.appPackageName ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
		when (status) {
			PackageInstaller.STATUS_SUCCESS -> {
				if (pkg != null) AppInstallationNotifier.onPackageInstalled(context, sessionId, pkg, install)
			}

			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				val action = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
				if (action.component?.packageName ?: action.`package` == context.packageName) return    // Prevent targeting our private component
				action.flags = action.flags and FLAG_GRANT_READ_URI_PERMISSION.inv() and FLAG_GRANT_WRITE_URI_PERMISSION.inv()  // Prevent content leak
				if (AppInstallerUtils.ensureSystemPackageEnabledAndUnfrozen(context, action))
					context.startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_FORWARD_RESULT))
			}

			else -> {
				if (status == PackageInstaller.STATUS_FAILURE_ABORTED && legacyStatus == INSTALL_FAILED_ABORTED)    // Aborted by user or us, no explicit feedback needed.
					return AppInstallationNotifier.onInstallAbort(context, sessionId, install)

				var message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
				if (message == null) message = context.getString(R.string.dialog_install_unknown_failure_message)
				else if (status == PackageInstaller.STATUS_FAILURE_INVALID && message.endsWith("base package"))     // Possible message: "Full install must include a base package"
					return Unit.also { Log.i(TAG, "APK is split, previous session dropped.") }

				Analytics.`$`().event("installer_failure").with(Analytics.Param.CONTENT, message).send()

				if (isMiui()) if (status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE && legacyStatus == INSTALL_FAILED_USER_RESTRICTED
						|| status == PackageInstaller.STATUS_FAILURE && message == "INSTALL_FAILED_INTERNAL_ERROR: Permission Denied")
							message += "\n\n${context.getString(R.string.prompt_miui_optimization)}"       // TODO: Alternate?
				AppInstallationNotifier.onInstallFail(context, sessionId, install, message)
			}
		}
	}

	companion object {
		private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"    // PackageInstall.EXTRA_LEGACY_STATUS
		private const val INSTALL_FAILED_INTERNAL_ERROR = -110      // ...
		private const val INSTALL_FAILED_USER_RESTRICTED = -111     // ...
		private const val INSTALL_FAILED_ABORTED = -115

		@JvmStatic fun createCallback(context: Context, install: AppInstallInfo, sessionId: Int): PendingIntent =
				Intent(context, AppInstallerStatusReceiver::class.java)     // Wrap in a bundle to avoid "ClassNotFoundException when unmarshalling" in system process
						.putExtra(EXTRA_INSTALL_INFO, Bundle().apply { putParcelable(null, install) }).let {
					PendingIntent.getBroadcast(context, sessionId, it, PendingIntent.FLAG_UPDATE_CURRENT) }


		@Suppress("SpellCheckingInspection") private const val TAG = "Island.AISR"
	}
}
