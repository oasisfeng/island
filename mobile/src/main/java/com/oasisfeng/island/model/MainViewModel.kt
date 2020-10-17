package com.oasisfeng.island.model

import android.app.Application
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.UserHandle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import com.google.android.material.tabs.TabLayout
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.LiveProfileStates
import com.oasisfeng.island.data.LiveProfileStates.ProfileState
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.settings.IslandNameManager
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId

class MainViewModel(app: Application, state: SavedStateHandle): AppListViewModel(app, state) {

	private val mProfileStates = LiveProfileStates(app)

	fun initializeTabs(activity: FragmentActivity, tabs: TabLayout) {
		tabs.tabIconTint = null
		tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) = onTabSwitched(activity, tabs, tab)
			override fun onTabUnselected(tab: TabLayout.Tab) {}
			override fun onTabReselected(tab: TabLayout.Tab) {}})

		// Tab "Discovery" and "Mainland" are always present
		tabs.addTab(tabs.newTab().setText(R.string.tab_discovery),/* selected = */false)
		val currentProfile = currentProfile
		tabs.addTab(tabs.newTab().setText(R.string.tab_mainland),/* selected = */Users.owner == currentProfile)

		for ((profile, name) in IslandNameManager.getAllNames(activity)) {
			val tab = tabs.newTab().setTag(profile).setText(name)
			mProfileStates.get(profile).observe(activity, { updateTabIconForProfileState(activity, tab, profile, it) })
			tabs.addTab(tab,/* selected = */profile == currentProfile) }
	}
}

private fun updateTabIconForProfileState(context: Context, tab: TabLayout.Tab, profile: UserHandle, state: ProfileState) {
	Log.d(TAG, "Update tab icon for profile ${profile.toId()}: $state")
	val icon = context.getDrawable(R.drawable.ic_island_black_24dp)!!
	icon.setTint(context.getColor(if (state == ProfileState.UNAVAILABLE) R.color.state_frozen else R.color.state_alive))
	try { tab.icon = context.packageManager.getUserBadgedIcon(icon, profile) }
	// (Mostly "vivo" devices before Android Q) "SecurityException: You need MANAGE_USERS permission to: check if specified user a managed profile outside your profile group"
	catch (e: SecurityException) { if (SDK_INT >= Q) analytics().logAndReport(TAG, "Error getting user badged icon", e) }
}

private const val TAG = "Island.MVM"