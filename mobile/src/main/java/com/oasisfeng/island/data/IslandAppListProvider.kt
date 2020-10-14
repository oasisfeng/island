package com.oasisfeng.island.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_USER
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.UserHandle
import android.util.ArrayMap
import android.util.Log
import com.oasisfeng.android.content.pm.LauncherAppsCompat
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.common.app.AppListProvider
import com.oasisfeng.island.data.helper.installed
import com.oasisfeng.island.engine.ClonedHiddenSystemApps
import com.oasisfeng.island.provisioning.CriticalAppsManager
import com.oasisfeng.island.provisioning.SystemAppsManager
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.streams.asSequence

/**
 * Island-specific [AppListProvider]
 *
 * Created by Oasis on 2016/8/10.
 */
class IslandAppListProvider : AppListProvider<IslandAppInfo>() {

	operator fun get(pkg: String, profile: UserHandle): IslandAppInfo? {
		return if (Users.isOwner(profile)) super.get(pkg) else loadAppsInProfileIfNotYet(profile)[pkg]
	}

	fun isInstalled(pkg: String, profile: UserHandle) = get(pkg, profile)?.run { installed && shouldShowAsEnabled() } == true

	fun isExclusive(app: IslandAppInfo): Boolean {
		if (Users.isOwner(app.user) && ! Users.hasProfile()) return true
		return Users.getProfilesManagedByIsland().asSequence().plus(Users.owner).minus(app.user).all { profile ->
			! isInstalled(app.packageName, profile) }
	}

	override fun createEntry(current: ApplicationInfo, last: IslandAppInfo?): IslandAppInfo {
		return IslandAppInfo(this, UserHandles.getUserHandleForUid(current.uid), current, last)
	}

	override fun onAppLabelUpdate(pkg: String, label: String) {
		super.onAppLabelUpdate(pkg, label)
		Users.getProfilesManagedByIsland().forEach { profile ->
			val appsInProfile = mIslandAppMap[profile] ?: return@forEach
			val entry = appsInProfile[pkg] ?: return@forEach
			Log.d(TAG, "Label updated for $pkg in profile $profile: $label")
			val newEntry = IslandAppInfo(this, Users.profile, entry, null)
			appsInProfile[pkg] = newEntry
			notifyUpdate(setOf(newEntry))
		}
	}

	fun installedApps(profile: UserHandle): Stream<IslandAppInfo> {
		return if (Users.isOwner(profile)) installedAppsInOwnerUser() else loadAppsInProfileIfNotYet(profile).values.stream()
	}

	private fun loadAppsInProfileIfNotYet(profile: UserHandle): Map<String, IslandAppInfo>
			= if (! Users.isProfileManagedByIsland(profile)) emptyMap()
			else mIslandAppMap.getOrPut(profile) { ArrayMap<String, IslandAppInfo>().apply { refresh(this, profile) }}

	private fun initializeMonitor() {
		Log.d(TAG, "Initializing monitor...")

		mLauncherApps.registerCallback(mCallback)

		context().registerReceiver(object : BroadcastReceiver() {
			override fun onReceive(context: Context, intent: Intent) {
				val profile = intent.getParcelableExtra<UserHandle>(EXTRA_USER) ?: return
				Log.i(TAG, "Profile removed: ${profile.toId()}")
				mIslandAppMap[profile]?.clear()
			}
		}, IntentFilter(Intent.ACTION_MANAGED_PROFILE_REMOVED))

		mClonedHiddenSystemApps.migrateIfNeeded()
	}

	private fun refresh(outApps: MutableMap<String, IslandAppInfo>, profile: UserHandle) {
		val la = mLauncherApps
		val visible = la.getActivityList(null, profile).asSequence().map { it.applicationInfo }.associateBy { it.packageName }  // Collect all unfrozen apps first in one API call.
		super.installedAppsInOwnerUser().asSequence().mapNotNull { app -> val pkg = app.packageName
			visible[pkg] ?: la.getAppInfo(pkg, MATCH_UNINSTALLED_PACKAGES, profile)?.takeIf { it.installed }}
				.forEach { info -> outApps[info.packageName] = IslandAppInfo(this, profile, info, null) }
		Log.d(TAG, "Refreshed apps in Island ${profile.toId()}")
	}

	private fun LauncherApps.getAppInfo(pkg: String, flags: Int, user: UserHandle): ApplicationInfo?
			= LauncherAppsCompat.getApplicationInfoNoThrows(this, pkg, flags, user)
	private fun getApplicationInfo(pkg: String, profile: UserHandle): ApplicationInfo? {
		// Use MATCH_UNINSTALLED_PACKAGES to include frozen packages and then exclude non-installed packages with FLAG_INSTALLED.
		val info = LauncherAppsCompat.getApplicationInfoNoThrows(mLauncherApps, pkg, MATCH_UNINSTALLED_PACKAGES, profile)
		return info?.takeIf { it.installed }
	}

	fun refreshPackage(pkg: String, profile: UserHandle, add: Boolean) {
		Log.d(TAG, "Update: " + pkg + if (add) " for pkg add" else " for pkg change")
		val info = getApplicationInfo(pkg, profile)
		val appsInProfile = mIslandAppMap[profile] ?: return
		if (info == null) {
			appsInProfile.remove(pkg)?.also { notifyRemoval(setOf(it)) }
			return }
		val last = appsInProfile[pkg]
		val app = IslandAppInfo(this, profile, info, last)
		if (add && app.isHidden) {
			Log.w(TAG, "Correct the flag for unhidden package: $pkg")
			app.isHidden = false }
		appsInProfile[pkg] = app
		notifyUpdate(setOf(app))
	}

	/** Freezing or disabling a critical app may cause malfunction to other apps or the whole system.  */
	fun isCritical(pkg: String): Boolean {
		return pkg == CriticalAppsManager.getCurrentWebViewPackageName() || mCriticalSystemPackages.contains(pkg)
	}

	private val mCallback: LauncherApps.Callback = object : LauncherApps.Callback() {

		override fun onPackageRemoved(pkg: String, profile: UserHandle) {
			val appsInProfile = mIslandAppMap[profile] ?: return
			val app = appsInProfile[pkg] ?: return Unit.also { Log.e(TAG, "Removed package not found in Island: $pkg") }
			if (app.isHidden) return  // The removal callback is triggered by freezing.
			val info = getApplicationInfo(pkg, profile)
			if (info != null && info.flags and ApplicationInfo.FLAG_INSTALLED != 0) {    // Frozen
				val newInfo = IslandAppInfo(this@IslandAppListProvider, profile, info, appsInProfile[pkg])
				if (!newInfo.isHidden) {
					Log.w(TAG, "Correct the flag for hidden package: $pkg")
					newInfo.isHidden = true
				}
				appsInProfile[pkg] = newInfo
				notifyUpdate(setOf(newInfo))
			} else {    // Uninstalled in profile
				val removedApp = appsInProfile.remove(pkg)
				if (removedApp != null) notifyRemoval(setOf(removedApp))
			}
		}

		override fun onPackageAdded(pkg: String, user: UserHandle) {
			refreshPackage(pkg, user, true)
		}

		override fun onPackageChanged(pkg: String, user: UserHandle) {
			refreshPackage(pkg, user, false) // TODO: Filter out component-level changes
		}

		override fun onPackagesSuspended(pkgs: Array<out String>, user: UserHandle) = pkgs.forEach { pkg ->
			refreshPackage(pkg, user, false)
		}

		override fun onPackagesUnsuspended(pkgs: Array<out String>, user: UserHandle) = pkgs.forEach { pkg ->
			refreshPackage(pkg, user, false)
		}

		override fun onPackagesAvailable(pkgs: Array<String>, user: UserHandle, replacing: Boolean) { Log.e(TAG, "onPackagesAvailable() is unsupported") }
		override fun onPackagesUnavailable(pkgs: Array<String>, user: UserHandle, replacing: Boolean) { Log.e(TAG, "onPackagesUnavailable() is unsupported") }
	}

	private val mIslandAppMap by lazy { initializeMonitor(); ArrayMap<UserHandle, MutableMap<String, IslandAppInfo>>() }
	private val mLauncherApps by lazy { context().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps }
	private val mClonedHiddenSystemApps by lazy { ClonedHiddenSystemApps(context()) }
	private val mCriticalSystemPackages by lazy { SystemAppsManager.detectCriticalSystemPackages(context().packageManager) }

	companion object {
		@JvmStatic fun getInstance(context: Context): IslandAppListProvider = AppListProvider.getInstance(context)
		@JvmStatic fun excludeSelf(context: Context) = exclude(context.packageName)
		private fun exclude(pkg: String) = Predicate { app: IslandAppInfo -> pkg != app.packageName }
	}
}

private const val TAG = "Island.AppListProv"
