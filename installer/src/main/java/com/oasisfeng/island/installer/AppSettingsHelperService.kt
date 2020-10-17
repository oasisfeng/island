package com.oasisfeng.island.installer

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.Users
import kotlinx.coroutines.GlobalScope

private const val HELPER_NOTIFICATION_TIMEOUT = 10_000L

class AppSettingsHelperService: Service() {

	private fun shouldEnableForceStop(pkg: String, uid: Int): Boolean {
		val appId = UserHandles.getAppId(uid)
		if (uid != appId) return true   // There's no "install from" entry in App Settings for apps in managed profile.
		if (appId == UserHandles.getAppId(Process.myUid())) return false
		return true
		// TODO
		//try { packageManager.getInstallerPackageName(pkg) != packageName }
		//catch (e: PackageManager.NameNotFoundException) { false }
	}

	override fun onCreate() {
		registerReceiver(mPackageEventReceiver, IntentFilter(ACTION_QUERY_PACKAGE_RESTART)
				.apply { addAction(Intent.ACTION_PACKAGE_RESTARTED); addDataScheme("package") })
	}

	override fun onDestroy() {
		try { unregisterReceiver(mPackageEventReceiver) } catch (e: IllegalArgumentException) {}
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		if (intent.action == Intent.ACTION_PACKAGE_RESTARTED)       // Forwarded from
			mPackageEventReceiver.onReceive(this, intent)
		return super.onStartCommand(intent, flags, startId)
	}

	override fun onBind(intent: Intent?) = Binder()     // Returning non-null to keep this service running

	private val mPackageEventReceiver: BroadcastReceiver = PackageEventReceiver()

	inner class PackageEventReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
		val pkg = intent.data?.schemeSpecificPart ?: return
		val uid = intent.getIntExtra(Intent.EXTRA_UID, -1).takeIf { it >= 0 }
				?: return Unit.also { Log.w(TAG, "Missing EXTRA_UID") }

		val uptimeMillis = SystemClock.uptimeMillis()
		if (intent.action == Intent.ACTION_PACKAGE_RESTARTED) {     // If triggered by system Settings, it will be followed by ACTION_QUERY_PACKAGE_RESTART immediately.
			return Unit.also { Shuttle(context, to = Users.owner).launch(at = GlobalScope) {
				try { onPackageRestarted(pkg, uid, uptimeMillis) }
				catch (e: RuntimeException) { analytics().logAndReport(TAG, "Error transferring ACTION_PACKAGE_RESTARTED to parent user", e) }}}}

		if (intent.action == ACTION_QUERY_PACKAGE_RESTART) {
			if (sLastPackageRestart?.run { first + MAX_DELAY > uptimeMillis && second == pkg && third == uid } == true) {
				return AppInstallationNotifier.showNotification(context, pkg, UserHandles.of(UserHandles.getUserId(uid)),
						Apps.of(context).getAppName(pkg), timeout = HELPER_NOTIFICATION_TIMEOUT) }

			sLastPackageRestart = null
			if (resultCode != Activity.RESULT_OK && shouldEnableForceStop(pkg, uid)) {
				resultCode = Activity.RESULT_OK
				if (! sToastShown) {
					Toast.makeText(context, R.string.app_settings_helper_prompt, Toast.LENGTH_LONG).show()
					sToastShown = true
				}
			}
		}
	}}

	companion object {

		private fun onPackageRestarted(pkg: String, uid: Int, uptimeMillis: Long) {
			sLastPackageRestart = Triple(uptimeMillis, pkg, uid)
		}

		var sLastPackageRestart: Triple<Long/* uptimeMillis */, String/* pkg */, Int/* uid */>? = null
		var sToastShown = false
	}
}

private const val MAX_DELAY = 2_000
private const val ACTION_QUERY_PACKAGE_RESTART = "android.intent.action.QUERY_PACKAGE_RESTART"  // Intent.ACTION_QUERY_PACKAGE_RESTART

private const val TAG = "Island.ASH"