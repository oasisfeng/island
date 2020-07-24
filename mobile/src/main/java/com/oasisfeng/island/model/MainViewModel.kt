package com.oasisfeng.island.model

import android.app.Application
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N_MR1
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import com.google.android.material.tabs.TabLayout
import com.oasisfeng.island.data.LiveProfileStates
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.settings.IslandNameManager
import com.oasisfeng.island.util.Users

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
			mProfileStates.get(profile).observe(activity, Observer { updateTabIconForProfileState(activity, tab, profile) })
			tabs.addTab(tab,/* selected = */profile == currentProfile) }

		if (tabs.tabCount > 3) tabs.tabMode = TabLayout.MODE_SCROLLABLE
	}
}

private fun updateTabIconForProfileState(context: Context, tab: TabLayout.Tab, profile: UserHandle) {
	val active = context.getSystemService<UserManager>()!!.run {
		if (SDK_INT >= N_MR1) isUserRunning(profile) else ! isQuietModeEnabled(profile) }
	val icon = context.getDrawable(R.drawable.ic_island_black_24dp)!!
	icon.setTint(context.getColor(if (active) R.color.state_alive else R.color.state_frozen))
	tab.icon = context.packageManager.getUserBadgedIcon(icon, profile)
}
