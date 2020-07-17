package com.oasisfeng.island.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import android.util.ArrayMap
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object IslandNameManager {

	@JvmStatic fun getAllNames(context: Context): Map<UserHandle, String> {
		val profiles = Users.getProfilesManagedByIsland()
		return ArrayMap<UserHandle, String>(profiles.size).apply {
			if (profiles.isNotEmpty()) getStore(context).also {
				profiles.forEach { profile ->
					val saved = getStore(context).getString(buildIslandNameKey(context, profile.toId()), null)
					put(profile, saved ?: getDefaultName(context, profile)) }}}
	}

	fun getDefaultName(context: Context, profile: UserHandle = Users.current()) = when (val profileId = profile.toId()) {
		10   -> context.getString(R.string.default_island0_name)
		11   -> context.getString(R.string.default_island1_name)
		12   -> context.getString(R.string.default_island2_name)
		13   -> context.getString(R.string.default_island3_name)
		else -> context.getString(R.string.default_island_name, profileId)
	}

	@ProfileUser fun syncNameToOwnerUser(context: Context, name: String) = GlobalScope.launch { // TODO: syncNameToParentProfile() with proper "parent".
		Shuttle(context, to = Users.owner).invoke(Users.current()) { profile -> saveProfileName(this, profile, name) }
	}

	@OwnerUser private fun saveProfileName(context: Context, profile: UserHandle, name: String)
			= getStore(context).edit().putString(buildIslandNameKey(context, profile.toId()), name).apply()

	@Suppress("DEPRECATION") private fun getStore(context: Context): SharedPreferences {
		return android.preference.PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())
	}

	@OwnerUser private fun buildIslandNameKey(context: Context, user: Int) = context.getString(R.string.key_island_name) + "." + user
}
