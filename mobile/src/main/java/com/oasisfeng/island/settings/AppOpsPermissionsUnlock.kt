package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.util.Log
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.util.DPM
import com.oasisfeng.island.util.DevicePolicies
import kotlin.concurrent.thread

class AppOpsPermissionsUnlock: BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent?) {
		if (intent?.action !in listOf(Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_LOCKED_BOOT_COMPLETED)) return
		if (SDK_INT < P || ! DevicePolicies(context).isProfileOwner) return disableSelf(context)

		val async = goAsync()
		thread(start = true) {
			try { unlockAll(context.applicationContext) } // SecurityException on BBK brands (OnePlus, Realme, OPPO and etc.), "getPackagesForUid: UID 1010256 requires android.permission.INTERACT_ACROSS_USERS_FULL or android.permission.INTERACT_ACROSS_USERS or android.permission.INTERACT_ACROSS_PROFILES to access user ."
			catch (e: SecurityException) { Log.e(TAG, "Failed to unlock all permissions", e) }
			disableSelf(context)
			async.finish()
		}
	}

	companion object {

		fun unlockAll(context: Context) {
			Log.i(TAG, "Start to unlock all permissions...")
			val policies = DevicePolicies(context)
			context.packageManager.getInstalledPackages(GET_PERMISSIONS or MATCH_UNINSTALLED_PACKAGES).forEach { info ->
				info.requestedPermissionsFlags?.forEachIndexed { i, flags ->
					if (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) {
						val permission = info.requestedPermissions[i]!!
						try { unlock(policies, info.applicationInfo, permission) }
						catch (e: RuntimeException) { Log.e(TAG, "Error unlocking permission for ${info.packageName}: $permission") }}}}
		}

		private fun unlock(policies: DevicePolicies, info: ApplicationInfo, permission: String) {
			val pkg = info.packageName
			val state = policies.invoke(DevicePolicyManager::getPermissionGrantState, pkg, permission)  // setPermissionGrantState() works for hidden app
			if (state == PERMISSION_GRANT_STATE_DEFAULT) return     // Not locked
			Log.i(TAG, "Unlock permission for $pkg: $permission")
			val hidden = info.hidden    // setPermissionGrantState() does not work for hidden app
			if (hidden) policies.invoke(DPM::setApplicationHidden, pkg, false)
			policies.invoke(DPM::setPermissionGrantState, pkg, permission, PERMISSION_GRANT_STATE_DEFAULT)
			if (hidden) policies.invoke(DPM::setApplicationHidden, pkg, true)
		}

		private fun disableSelf(context: Context) = context.packageManager.setComponentEnabledSetting(
				ComponentName(context, AppOpsPermissionsUnlock::class.java), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
	}
}

@Suppress("SpellCheckingInspection") private const val TAG = "Island.AOPU"