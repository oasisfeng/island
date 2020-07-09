package com.oasisfeng.island.settings

import android.content.Context
import android.content.SharedPreferences
import com.oasisfeng.android.base.SparseArray
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.shuttle.PendingIntentShuttle
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId

object IslandNameManager {

	@JvmStatic fun getAllNames(context: Context): SparseArray<String> {
		val profiles = Users.getProfilesManagedByIsland()
		val names = SparseArray<String>(profiles.size)
		getStore(context).also {
			profiles.forEach { profile ->
				val profileId = profile.toId()
				val saved = getStore(context).getString(buildIslandNameKey(context, profileId), null)
				names.put(profileId, saved ?: getDefaultName(context, profileId)) }}
		return names
	}

	fun getDefaultName(context: Context, profileId: Int = Users.current().toId()) = when (profileId) {
		10   -> context.getString(R.string.default_island0_name)
		11   -> context.getString(R.string.default_island1_name)
		12   -> context.getString(R.string.default_island2_name)
		13   -> context.getString(R.string.default_island3_name)
		else -> context.getString(R.string.default_island_name, profileId)
	}

	@ProfileUser fun syncNameToOwnerUser(context: Context, name: String) {  // TODO: syncNameToParentProfile()
		val key = buildIslandNameKey(context, Users.current().toId())
		PendingIntentShuttle.shuttle(context, Users.owner, key, name) @Suppress("NAME_SHADOWING") { key, name ->
			getStore(this).edit().putString(key, name).apply() }
	}

	@Suppress("DEPRECATION") private fun getStore(context: Context): SharedPreferences {
		return android.preference.PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())
	}

	@OwnerUser private fun buildIslandNameKey(context: Context, user: Int) = context.getString(R.string.key_island_name) + "." + user
}
