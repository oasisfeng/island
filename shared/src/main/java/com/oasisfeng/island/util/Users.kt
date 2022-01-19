package com.oasisfeng.island.util

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N_MR1
import android.os.Build.VERSION_CODES.Q
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.getSystemService
import com.oasisfeng.android.content.IntentFilters
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.pattern.PseudoContentProvider
import java.util.*

/**
 * Utility class for user-related helpers. Only works within the process where this provider is declared to be running.
 *
 * Created by Oasis on 2016/9/25.
 */
class Users : PseudoContentProvider() {

	override fun onCreate(): Boolean {
		Log.v(TAG, "onCreate()")
		val priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
		context().registerReceiver(mProfileChangeObserver,
			IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED,  // ACTION_MANAGED_PROFILE_ADDED is sent by DevicePolicyManagerService.setProfileEnabled()
				Intent.ACTION_MANAGED_PROFILE_REMOVED,
				DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED // ACTION_PROFILE_OWNER_CHANGED is sent after "dpm set-profile-owner ..."
			).inPriority(priority))
		refreshUsers(context())
		return true
	}

	private val mProfileChangeObserver: BroadcastReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
		val added = intent.action == Intent.ACTION_MANAGED_PROFILE_ADDED
		val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
		Log.i(TAG, (if (added) "Profile added: " else "Profile removed: ") + (user?.toId()?.toString() ?: "null"))
		refreshUsers(context)
	}}

	companion object {
		@JvmField var profile: UserHandle? = null // The first profile managed by Island (semi-immutable, until profile is created or destroyed)
		@JvmStatic lateinit var parentProfile: UserHandle; private set
		@JvmStatic fun hasProfile() = profile != null

		private val CURRENT: UserHandle = Process.myUserHandle()
		private val CURRENT_ID = CURRENT.toId()
		@JvmStatic fun current() = CURRENT
		@JvmStatic fun currentId() = CURRENT_ID

		/** This method should not be called under normal circumstance.  */
		@JvmStatic fun refreshUsers(context: Context) {
			mDebugBuild = context.applicationInfo.flags and FLAG_DEBUGGABLE != 0
			val um = context.getSystemService<UserManager>()!!
			val profiles = um.userProfiles
			val profilesByIsland = ArrayList<UserHandle>(profiles.size - 1)
			parentProfile = profiles[0]
			if (parentProfile == CURRENT) {      // Running in parent profile
				val uiModule = Modules.getMainLaunchActivity(context).packageName
				val la = context.getSystemService<LauncherApps>()!!
				val activityInOwner = la.getActivityList(uiModule, CURRENT)[0].name
				for (i in 1 /* skip parent */ until profiles.size) {
					val profile = profiles[i]
					for (activity in la.getActivityList(uiModule, profile))
						// Separate "Island Settings" launcher activity is enabled, only if profile is managed by Island.
						if (activity.name == activityInOwner) Log.i(TAG, "Profile not managed by Island: ${profile.toId()}")
						else profilesByIsland.add(profile).also { Log.i(TAG, "Profile managed by Island: ${profile.toId()}") }
				}}
			else for (i in 1 /* skip parent */ until profiles.size) {
				val user = profiles[i]
				if (user != CURRENT) Log.w(TAG, "Skip sibling profile (may not managed by Island): ${user.toId()}")
				else profilesByIsland.add(user).also { Log.i(TAG, "Profile managed by Island: ${user.toId()}") }}

			profile = if (profilesByIsland.isEmpty()) null else profilesByIsland[profilesByIsland.size - 1]

			profilesByIsland.sortWith(Comparator.comparing { um.getSerialNumberForUser(it) })
			sProfilesManagedByIsland = profilesByIsland

			try { isProfileManagedByIsland = DevicePolicies(context).isProfileOwner }
			catch (e: RuntimeException) { Log.e(TAG, "Error checking current profile", e) }
		}

		fun isProfileRunning(context: Context, user: UserHandle): Boolean {
			if (CURRENT == user) return true
			val um = context.getSystemService<UserManager>()!!
			if (SDK_INT >= N_MR1)
				try { return um.isUserRunning(user) }
				catch (e: RuntimeException) { Log.w(TAG, "Error checking running state for user ${user.toId()}") }
			return um.isQuietModeEnabled(user)
		}

		@JvmStatic fun isSystemUser() = CURRENT_ID == 0
		@JvmStatic fun isParentProfile() = CURRENT_ID == parentProfile.toId()
		@JvmStatic fun isParentProfile(user: UserHandle) = user == parentProfile
		@JvmStatic fun isParentProfile(user_id: Int) = user_id == parentProfile.toId()
		@JvmStatic var isProfileManagedByIsland = false; private set

		@OwnerUser @JvmStatic fun isProfileManagedByIsland(user: UserHandle): Boolean {
			ensureParentProfile()
			if (isParentProfile(user)) {
				if (isParentProfile()) return isProfileManagedByIsland
				throw IllegalArgumentException("Not working for profile parent user") }
			return sProfilesManagedByIsland.contains(user)
		}

		/** Excluding parent profile  */
		@OwnerUser @JvmStatic fun getProfilesManagedByIsland() = sProfilesManagedByIsland.also { ensureParentProfile() }
		@JvmStatic fun UserHandle.toId() = hashCode()
		@JvmStatic fun isSameApp(uid1: Int, uid2: Int) = getAppId(uid1) == getAppId(uid2)
		private fun getAppId(uid: Int) = uid % PER_USER_RANGE
		private fun ensureParentProfile() = check(! mDebugBuild || isParentProfile()) { "Not called in owner user" }

		fun getUserBadgedIcon(context: Context, icon: Drawable, user: UserHandle) =
			try { context.packageManager.getUserBadgedIcon(icon, user) }
			catch (e: SecurityException) {    // (Mostly "Vivo" devices before Android Q) "SecurityException: You need MANAGE_USERS permission to: check if specified user a managed profile outside your profile group"
				icon.also { if (SDK_INT >= Q) analytics().logAndReport(TAG, "Error getting user badged icon", e) }}

		private var mDebugBuild = false
		private lateinit var sProfilesManagedByIsland: List<UserHandle> // Intentionally left null to fail early if this class is accidentally used in non-default process.
		private const val PER_USER_RANGE = 100000
		private const val TAG = "Island.Users"
	}
}