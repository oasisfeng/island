package com.oasisfeng.island.settings

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
import android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.XmlRes
import androidx.core.app.NavUtils
import androidx.core.content.getSystemService
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.island.MainActivity
import com.oasisfeng.island.mobile.BuildConfig
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.settings.IslandNameManager.getAllNames
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Users

@Suppress("DEPRECATION") class SettingsActivity : android.preference.PreferenceActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val actionBar = actionBar
		actionBar?.setDisplayHomeAsUpEnabled(true) // Show the Up button in the action bar.
	}

	override fun onResume() {
		super.onResume()
		if (! DevicePolicies(this).isProfileOrDeviceOwnerOnCallingUser) {
			val profiles = getSystemService<UserManager>()!!.userProfiles
			if (profiles.size == 1) {     // The last Island is just destroyed
				Log.i(TAG, "Nothing left, back to initial setup.")
				finishAffinity()
				startActivity(Intent(this, MainActivity::class.java)) }}
	}

	override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			if (! super.onMenuItemSelected(featureId, item)) NavUtils.navigateUpFromSameTask(this)
			return true }
		return super.onMenuItemSelected(featureId, item)
	}

	override fun onIsMultiPane() = resources.configuration.screenLayout and SCREENLAYOUT_SIZE_MASK >= SCREENLAYOUT_SIZE_XLARGE
	override fun onBuildHeaders(target: List<Header>) = loadHeadersFromResource(R.xml.pref_headers, target)

	override fun onHeaderClick(header: Header, position: Int) {
		if (header.id != R.id.pref_header_island.toLong())
			return super.onHeaderClick(header, position)

		val users = ArrayList<UserHandle>() // Support multiple managed-profiles
		users.add(Users.owner)
		users.addAll(Users.getProfilesManagedByIsland())
		if (users.size <= 1)        // Managed mainland without Island
			return super.onHeaderClick(header, position)

		val names = getAllNames(this)
		val labels = users.map { user -> if (Users.isOwner(user)) getText(R.string.tab_mainland) else names[user] }.toTypedArray()
		Dialogs.buildList(this, null, labels) { _, which ->
			if (which == 0) super.onHeaderClick(header, position)
			else launchSettingsActivityAsUser(users[which])
		}.show()
	}

	private fun launchSettingsActivityAsUser(profile: UserHandle) {
		val la = getSystemService<LauncherApps>()!!
		val activities = la.getActivityList(packageName, profile)
		if (activities.isEmpty())
			return Toast.makeText(this, R.string.prompt_island_not_yet_setup, Toast.LENGTH_LONG).show()
		activities.firstOrNull { IslandSettingsActivity::class.java.name != it.componentName.className }?.also {
			val component = it.componentName
			Shuttle(this, true, profile).launch {
				startActivity(Intent().setComponent(component).addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_ANIMATION)) }}
	}

	/** This method stops fragment injection in malicious applications. Make sure to deny any unknown fragments here.  */
	override fun isValidFragment(fragmentName: String) = fragmentName.startsWith(javaClass.getPackage()!!.name)

	abstract class SubPreferenceFragment(@param:XmlRes private val mPreferenceXml: Int) : android.preference.PreferenceFragment() {

		override fun onCreate(savedInstanceState: Bundle?) {
			super.onCreate(savedInstanceState)
			addPreferencesFromResource(mPreferenceXml)
			setHasOptionsMenu(true)
		}

		override fun onOptionsItemSelected(item: MenuItem): Boolean {
			if (item.itemId == android.R.id.home) {
				startActivity(Intent(activity, SettingsActivity::class.java))
				return true }
			return super.onOptionsItemSelected(item)
		}
	}

	class AboutFragment : SubPreferenceFragment(R.xml.pref_about) {

		override fun onCreate(savedInstanceState: Bundle?) {
			super.onCreate(savedInstanceState)
			try {
				val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
				var summary: String
				if (BuildConfig.DEBUG) {
					summary = info.versionName + " (" + info.versionCode + ")"
					if (activity.packageName != Modules.MODULE_ENGINE) try {
						val engine = activity.packageManager.getPackageInfo(Modules.MODULE_ENGINE, 0)
						summary += ", Engine: " + engine.versionName + " (" + engine.versionCode + ")" }
					catch (ignored: PackageManager.NameNotFoundException) {}}
				else summary = info.versionName
				findPreference(getString(R.string.key_version)).summary = summary }
			catch (ignored: PackageManager.NameNotFoundException) {} // Should never happen
		}
	}

	companion object {

		@JvmStatic fun startWithPreference(context: Context, fragment: Class<out android.preference.PreferenceFragment>) {
			val intent = Intent(context, SettingsActivity::class.java).putExtra(EXTRA_SHOW_FRAGMENT, fragment.name)
			Activities.startActivity(context, intent)
		}
	}
}

private const val TAG = "Island.SA"
