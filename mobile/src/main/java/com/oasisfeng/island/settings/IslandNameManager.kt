package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.*
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

	private fun getDefaultName(context: Context, profile: UserHandle = Users.current()): String {
		if (Users.isParentProfile(profile)) return context.getString(R.string.tab_mainland)
		val islandCount = Users.run { if (isParentProfile()) getProfilesManagedByIsland().size else getProfileCount() - 1 }
		return if (islandCount > 1) getDefaultSpecificName(context, profile) else context.getString(R.string.default_island_name)
	}

	fun getDefaultSpecificName(context: Context, profile: UserHandle = Users.current()) = when (val profileId = profile.toId()) {
		0    -> context.getString(R.string.tab_mainland)
		10   -> context.getString(R.string.default_island0_name)
		11   -> context.getString(R.string.default_island1_name)
		12   -> context.getString(R.string.default_island2_name)
		13   -> context.getString(R.string.default_island3_name)
		else -> context.getString(R.string.default_islandN_name, profileId)
	}

	@ProfileUser fun syncNameToOwnerUser(context: Context, name: String)    // TODO: syncNameToParentProfile() with proper "parent".
			= Shuttle(context, to = Users.parentProfile).launch(with = Users.current()) { saveProfileName(this, it, name) }

	@OwnerUser @ProfileUser private fun saveProfileName(context: Context, profile: UserHandle?, name: String)
			= getStore(context).edit().putString(buildIslandNameKey(context, profile), name).apply()

	@ProfileUser fun getName(context: Context) =
		getStore(context).getString(buildIslandNameKey(context), null) ?: getDefaultName(context)

	@Suppress("DEPRECATION") private fun getStore(context: Context)
			= android.preference.PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())

	private fun buildIslandNameKey(context: Context, user: UserHandle? = null): String {
		val key = context.getString(R.string.key_island_name)
		return if (user != null) "$key.${user.toId()}" else key
	}

	@ProfileUser fun setName(context: Context, name: String) {  // Extra spaces for better readability in system UI (e.g. app Uninstall confirmation dialog)
		DevicePolicies(context).invoke(DevicePolicyManager::setProfileName, " $name ")
		saveProfileName(context, null, name)
		syncNameToOwnerUser(context, name)
	}

	class NameInitializer: BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent?) {
			if (intent?.action == Intent.ACTION_USER_INITIALIZE) setName(context, getDefaultName(context))
		}
	}
}
