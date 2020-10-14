package com.oasisfeng.island.watcher

import android.Manifest.permission
import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.*
import android.content.Intent.ACTION_PACKAGE_REMOVED
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.api.Api
import com.oasisfeng.island.notification.NotificationIds
import com.oasisfeng.island.notification.post
import com.oasisfeng.island.util.DPM
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.pattern.PseudoContentProvider
import java.util.*

/**
 * App watcher for unfrozen apps in Island, for convenient refreezing.
 *
 * Created by Oasis on 2019-2-27.
 */
@RequiresApi(O) class IslandAppWatcher : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val data = intent.data; val action = intent.action ?: return; val ssp = data?.schemeSpecificPart ?: return
		when (action) {
			ACTION_REFREEZE -> refreeze(context, ssp, intent.getStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS))
			ACTION_DISMISS  -> NotificationIds.IslandAppWatcher.cancel(context, if ("package" == data.scheme) ssp else data.toString())
			ACTION_PACKAGE_REMOVED,
			Intent.ACTION_PACKAGE_FULLY_REMOVED -> NotificationIds.IslandAppWatcher.cancel(context, ssp)
			DevicePolicies.ACTION_PACKAGE_UNFROZEN ->
				try {
					if (NotificationIds.IslandAppWatcher.isBlocked(context)) return
					val info = context.packageManager.getPackageInfo(ssp, PackageManager.GET_PERMISSIONS)
					Log.i(TAG, "App is available: $ssp")
					startWatching(context, info) }
				catch (e: PackageManager.NameNotFoundException) {
					Log.w(TAG, "App is unavailable: $ssp")
					NotificationIds.IslandAppWatcher.cancel(context, ssp) }
			ACTION_REVOKE_PERMISSION -> {
				val pkg = data.scheme!!; val policies = DevicePolicies(context)
				val hidden = policies.invoke(DPM::isApplicationHidden, pkg)
				if (hidden) policies.setApplicationHiddenWithoutAppOpsSaver(pkg, false) // setPermissionGrantState() only works for unfrozen app
				try {
					if (policies.invoke(DPM::setPermissionGrantState, pkg, ssp, DPM.PERMISSION_GRANT_STATE_DENIED))
						policies.invoke(DPM::setPermissionGrantState, pkg, ssp, DPM.PERMISSION_GRANT_STATE_DEFAULT)
					else Log.e(TAG, "Failed to revoke permission $ssp for $pkg") }
				finally { if (hidden) policies.setApplicationHiddenWithoutAppOpsSaver(pkg, true) }
				NotificationIds.IslandAppWatcher.cancel(context, data.toString()) }}
	}

	private fun refreeze(context: Context, pkg: String, watching_permissions: List<String>?) {
		if (mCallerId == null) mCallerId = PendingIntent.getBroadcast(context, 0, Intent(), FLAG_UPDATE_CURRENT)
		context.sendBroadcast(Intent(Api.latest.ACTION_FREEZE, Uri.fromParts("package", pkg, null))
				.putExtra(Api.latest.EXTRA_CALLER_ID, mCallerId).setPackage(context.packageName))
		if (watching_permissions == null) return
		val pm = context.packageManager
		val grantedPermissions = watching_permissions.filter { pm.checkPermission(it, pkg) == PERMISSION_GRANTED }.toTypedArray()
		if (grantedPermissions.isEmpty()) return

		val appName = Apps.of(context).getAppName(pkg)
		for (granted_permission in grantedPermissions) try {
			val permissionLabel = pm.getPermissionInfo(granted_permission, 0).loadLabel(pm)
			val tag = Uri.fromParts(pkg, granted_permission, null).toString()
			NotificationIds.IslandAppWatcher.post(context, tag) { buildShared(context, pkg, R.color.accent)
					setSubText(appName).setContentTitle(context.getString(R.string.notification_permission_was_granted_title, permissionLabel))
					setContentText(context.getText(R.string.notification_permission_was_granted_text))
					addAction(Notification.Action.Builder(null, context.getText(R.string.action_keep_granted),
							makePendingIntent(context, ACTION_DISMISS, pkg, granted_permission)).build())
					addAction(Notification.Action.Builder(null, context.getText(R.string.action_revoke_granted),
							makePendingIntent(context, ACTION_REVOKE_PERMISSION, pkg, granted_permission)).build()) }}
		catch (_: PackageManager.NameNotFoundException) {}  // Should never happen
	}

	private var mCallerId: PendingIntent? = null

	/** This provider tracks all freezing and unfreezing events triggered by other modules within the default process of Island  */
	class AppStateTracker : PseudoContentProvider() {

		override fun onCreate() = false.also { context().registerReceiver(IslandAppWatcher(), IntentFilter().apply {
			addAction(DevicePolicies.ACTION_PACKAGE_UNFROZEN); addAction(ACTION_PACKAGE_REMOVED); addDataScheme(("package")) }) }
	}

	companion object {

		private val CONCERNED_PERMISSIONS: Collection<String> = listOf(
				permission.CAMERA, permission.RECORD_AUDIO, permission.READ_SMS, permission.RECEIVE_SMS,
				permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION)

		private const val ACTION_REFREEZE = "REFREEZE"
		private const val ACTION_DISMISS = "DISMISS"
		private const val ACTION_REVOKE_PERMISSION = "REVOKE_PERMISSION"
		private const val EXTRA_WATCHING_PERMISSIONS = "permissions" // ArrayList<String>

		private fun startWatching(context: Context, info: PackageInfo) {
			val watchingPermissions: ArrayList<String>?
			if (info.requestedPermissions != null) {
				watchingPermissions = ArrayList()
				for (i in info.requestedPermissions.indices) if (info.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
					val permission = info.requestedPermissions[i]
					if (CONCERNED_PERMISSIONS.contains(permission)) watchingPermissions.add(permission)
				}
			} else watchingPermissions = null
			val pkg = info.packageName; val appLabel = info.applicationInfo.loadLabel(context.packageManager)
			NotificationIds.IslandAppWatcher.post(context, pkg) { buildShared(context, pkg, R.color.primary)
					setContentTitle(context.getString(R.string.notification_app_watcher_title, appLabel)).setContentText(context.getText(R.string.notification_app_watcher_text))
					setContentIntent(makePendingIntent(context, ACTION_REFREEZE, "package", pkg) {
						watchingPermissions?.also { putStringArrayListExtra(EXTRA_WATCHING_PERMISSIONS, it) }})
					addAction(Notification.Action.Builder(null, context.getText(R.string.action_settings), PendingIntent.getActivity(context, 0,
							NotificationIds.IslandWatcher.buildChannelSettingsIntent(context), FLAG_UPDATE_CURRENT)).build())
					addAction(Notification.Action.Builder(null, context.getText(R.string.action_dismiss),
							makePendingIntent(context, ACTION_DISMISS, "package", pkg)).build()) }
		}

		private fun makePendingIntent(context: Context, action: String, scheme: String, ssp: String, extras: ((Intent).() -> Unit)? = null)
				= PendingIntent.getBroadcast(context, 0, Intent(action).setClass(context, IslandAppWatcher::class.java).apply {
					data = Uri.fromParts(scheme, ssp, null); extras?.invoke(this)
				}, FLAG_UPDATE_CURRENT)

		private fun Notification.Builder.buildShared(context: Context, pkg: String, @ColorRes color: Int) {
			val shortcutId = "launch:$pkg"
			setOngoing(true).setColor(context.getColor(color))
					.setSmallIcon(R.drawable.ic_landscape_black_24dp).setVisibility(Notification.VISIBILITY_PUBLIC)
					.setGroup(GROUP).setCategory(Notification.CATEGORY_STATUS).setShortcutId(shortcutId)
			if (SDK_INT >= Q) setLocusId(LocusId(shortcutId))
		}
	}
}

private const val TAG = "Island.AppWatcher"
