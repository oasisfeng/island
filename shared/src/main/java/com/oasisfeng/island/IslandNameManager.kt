package com.oasisfeng.island

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.oasisfeng.android.content.sendProtectedBroadcastInternally
import com.oasisfeng.island.shared.R
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.Users.Companion.ACTION_USER_INFO_CHANGED
import com.oasisfeng.island.util.Users.Companion.EXTRA_USER_HANDLE
import com.oasisfeng.island.util.Users.Companion.toId

object IslandNameManager {

	@OwnerUser @JvmStatic fun getAllNames(context: Context): Map<UserHandle, String> {
		val profiles = Users.getProfilesManagedByIsland()
		return when (profiles.size) {
			0 -> emptyMap()
			1 -> mapOf(Pair(profiles[0], context.getString(R.string.default_island_name)))
			else -> getStore(context).let { store -> profiles.associateWith { profile ->
					store.getString(buildIslandNameKey(context, profile), null) ?: getDefaultSpecificName(context, profile) }}}
	}

	@ProfileUser fun getName(context: Context) =
		getStore(context).getString(buildIslandNameKey(context), null) ?: getDefaultName(context)

	private fun getDefaultName(context: Context, profile: UserHandle = Users.current()): String {
		if (Users.isParentProfile(profile)) return context.getString(R.string.mainland_name)
		val islandCount = Users.run { if (isParentProfile()) getProfilesManagedByIsland().size else getProfileCount() - 1 }
		return if (islandCount > 1) getDefaultSpecificName(context, profile) else context.getString(R.string.default_island_name)
	}

	private fun getDefaultSpecificName(context: Context, profile: UserHandle = Users.current()) =
		when (val profileId = profile.toId()) {
			0    -> context.getString(R.string.mainland_name)
			10   -> context.getString(R.string.default_island0_name)
			11   -> context.getString(R.string.default_island1_name)
			12   -> context.getString(R.string.default_island2_name)
			13   -> context.getString(R.string.default_island3_name)
			else -> context.getString(R.string.default_islandN_name, profileId) }

	@OwnerUser @ProfileUser private fun saveProfileName(context: Context, profile: UserHandle?, name: String)
			= getStore(context).edit().putString(buildIslandNameKey(context, profile), name).apply()

	@Suppress("DEPRECATION") private fun getStore(context: Context) =
		android.preference.PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())

	private fun buildIslandNameKey(context: Context, user: UserHandle? = null): String {
		val key = context.getString(R.string.key_island_name)
		return if (user != null) "$key.${user.toId()}" else key
	}

	@ProfileUser fun setName(context: Context, name: String) {  // Extra spaces for better readability in system UI (e.g. app Uninstall confirmation dialog)
		DevicePolicies(context).invoke(DevicePolicyManager::setProfileName, " $name ")
		saveProfileName(context, null, name)
		sendProtectedBroadcastInternally(context, Intent(ACTION_USER_INFO_CHANGED).putExtra(EXTRA_USER_HANDLE, Users.currentId()))
		syncNameToParentProfile(context, name)
	}

	@ProfileUser fun syncNameToParentProfile(context: Context, name: String = getName(context))
			= Shuttle(context, to = Users.parentProfile).launchNoThrows(with = Users.current()) { saveProfileName(this, it, name) }

	class NameInitializer: BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent?) {
			if (intent?.action == Intent.ACTION_USER_INITIALIZE) setName(context, getDefaultName(context))
		}
	}
}
