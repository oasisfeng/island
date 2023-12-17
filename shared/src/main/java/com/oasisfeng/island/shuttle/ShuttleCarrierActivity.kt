package com.oasisfeng.island.shuttle

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification.GROUP_ALERT_SUMMARY
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.CrossProfileApps
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.util.Log
import androidx.core.content.getSystemService
import com.oasisfeng.island.engine.CrossProfile
import com.oasisfeng.island.notification.NotificationIds
import com.oasisfeng.island.notification.post
import com.oasisfeng.island.shared.R
import com.oasisfeng.island.util.DPM
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users

class ShuttleCarrierActivity: Activity() {

	companion object {

		@ProfileUser fun sendToParentProfileQuietlyIfPossible(context: Context, decoration: Intent.() -> Unit) {
			val intent = Intent(context, ShuttleCarrierActivity::class.java)
					.addFlags(FLAG_ACTIVITY_NEW_TASK or SILENT_LAUNCH_FLAGS).apply(decoration)
			if (isCredentialNeeded(context)) {
				return NotificationIds.Shuttle.post(context) { setOngoing(true).setVisibility(VISIBILITY_PUBLIC)
						.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getColor(R.color.accent))
						.setContentTitle(context.getString(R.string.notification_profile_shuttle_pending_title))
						.setContentText(context.getString(R.string.notification_profile_shuttle_pending_text))
						.setContentIntent(PendingIntent.getActivity(context, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE))
						.apply { if (SDK_INT >= O) setGroup("Shuttle").setGroupAlertBehavior(GROUP_ALERT_SUMMARY) }}}

			Log.d(TAG, "Starting trampoline to Mainland...")
			context.startActivity(intent)   // Start self in current profile as a trampoline to startActivityForResult() cross-profile.
		}
		/** Credential confirmation is needed if separate challenge (P+) is used and is locked by keyguard,
		 *  even if "user" (credential protected storage) is unlocked */
		@ProfileUser private fun isCredentialNeeded(context: Context) =
				if (SDK_INT < P || DevicePolicies(context).run { ! isManagedProfile || invoke(DPM::isUsingUnifiedPassword) }) false
				else (context.getSystemService<KeyguardManager>()?.run { isDeviceLocked && isDeviceSecure } ?: false)

		private const val ACTION = "com.oasisfeng.island.action.SHUTTLE"
		private const val SILENT_LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION or
				Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY //or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (Users.isParentProfile()) {
			Log.d(TAG, "Carrier is started in Mainland.")
			try { ShuttleProvider.collectActivityResult(this, intent) }
			catch (e: SecurityException) { Log.e(TAG, "Error collecting shuttle.") }
			setResult(RESULT_OK, Intent(null, ShuttleProvider.buildCrossProfileUri()) // Send reverse shuttle back
					.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
			finish()
		} else {
			Log.d(TAG, "Starting carrier in Mainland...")
			val intent = intent.setAction(ACTION).removeFlag(FLAG_ACTIVITY_NEW_TASK)
			try {
				if (SDK_INT >= Build.VERSION_CODES.R) {
					DevicePolicies(this).ensureCrossProfileReady()
					getSystemService<CrossProfileApps>()!!.startActivity(intent, Users.parentProfile, this)
				} else {
					DevicePolicies(this).addCrossProfileIntentFilter(IntentFilter(ACTION), FLAG_PARENT_CAN_ACCESS_MANAGED)
					CrossProfile.decorateIntentForActivityInParentProfile(this, intent.setComponent(null))
					startActivityForResult(intent, 1)
				}
			} catch (e: RuntimeException) { finish(); Log.e(TAG, "Error establishing shuttle to parent profile.", e) }}
	}

	override fun onRestart() {
		super.onRestart()
		if (SDK_INT >= Build.VERSION_CODES.R) try {       // Cannot receive activity result on R+, see onCreate().
			ShuttleProvider.takeUriGranted(this, Uri.parse(ShuttleProvider.CONTENT_URI))
		} catch (e: RuntimeException) { Log.e(TAG, "Error taking shuttle from parent profile.", e) }
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		Log.v(TAG, "onActivityResult: $data")
		ShuttleProvider.collectActivityResult(this, data ?: return)       // Receive the reverse shuttle sent back as activity result
		finish()
	}

	private fun Intent.removeFlag(flag: Int) = this.also { flags = flags and flag.inv() }
}

private const val TAG = "Island.SRA"