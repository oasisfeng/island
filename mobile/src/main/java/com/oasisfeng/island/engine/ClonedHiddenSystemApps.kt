package com.oasisfeng.island.engine

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.WorkerThread
import com.oasisfeng.android.content.pm.LauncherAppsCompat
import com.oasisfeng.android.util.SafeSharedPreferences
import com.oasisfeng.island.controller.IslandAppControl
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.data.helper.suspended
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.Users
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/** Track explicitly "cloned" (unfrozen) system apps (previous frozen in post-provisioning). Other frozen system apps should be treated as "disabled" in UI.  */
@OwnerUser class ClonedHiddenSystemApps(private val context: Context) {

	fun migrateIfNeeded() {
		val profile = Users.profile; val store = getStore(context, profile ?: return)
		if (store.getInt(PREF_KEY_VERSION, 0) > 0)
			GlobalScope.launch { migrate(store, profile).also { store.edit().clear().apply() }}
	}

	@WorkerThread private fun migrate(store: SharedPreferences, profile: UserHandle) {
		val flags = PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_UNINSTALLED_PACKAGES
		val pkgsToSuspend = context.packageManager.getInstalledApplications(flags).mapNotNull { it.packageName.takeIf { pkg ->
			mLauncherApps.getApplicationInfoNoThrows(pkg, flags, profile).let { app ->
				app != null && app.hidden && ! app.suspended && store.getInt(pkg, 0) != COMPONENT_ENABLED_STATE_ENABLED }}
		}.toTypedArray()
		if (pkgsToSuspend.isEmpty()) return

		Shuttle(context, to = profile).launch(at = GlobalScope, with = pkgsToSuspend) { pkgs ->
			IslandAppControl.setPackagesSuspended(this, pkgs, true).apply {
				if (isEmpty()) Log.i(TAG, "Migration finished")
				else Log.w(TAG, "${pkgs.size - size} migrated but $size failed: $this") }}
	}

	private val mLauncherApps by lazy { LauncherAppsCompat(context) }

	companion object {

		@JvmStatic fun isCloned(app: ApplicationInfo) = app.hidden && ! app.suspended

		@JvmStatic fun setCloned(context: Context, pkg: String)
				= DevicePolicies(context).invoke(DevicePolicyManager::setPackagesSuspended, arrayOf(pkg), false).isEmpty()

		private const val SHARED_PREFS_PREFIX_ENABLED_SYSTEM_APPS = "cloned_system_apps_u" /* + user serial number */
		private const val PREF_KEY_VERSION = "_version"

		private fun getStore(context: Context, user: UserHandle): SharedPreferences {
			val usn = context.getSystemService(UserManager::class.java)!!.getSerialNumberForUser(user)
			return SafeSharedPreferences.wrap(context.getSharedPreferences(SHARED_PREFS_PREFIX_ENABLED_SYSTEM_APPS + usn, Context.MODE_PRIVATE))
		}
	}
}

private const val TAG = "Island.HiddenSysApp"
