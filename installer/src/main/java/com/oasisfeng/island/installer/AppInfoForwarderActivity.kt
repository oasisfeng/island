package com.oasisfeng.island.installer

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_SEARCH
import android.content.pm.LabeledIntent
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.shuttle.ActivityShuttle
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.CallerAwareActivity
import com.oasisfeng.island.util.Users
import java.util.*

/**
 * Created by Oasis on 2018-11-16.
 */
class AppInfoForwarderActivity : CallerAwareActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val intent = intent.setComponent(null).setPackage(null)
		val user: UserHandle? = intent.getParcelableExtra(Intent.EXTRA_USER)
		if (user != null && intent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {  // For profiles other than default
			intent.removeExtra(Intent.EXTRA_USER)
			Shuttle(this, user).launch {
				startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
		} else intent.getStringExtra(IntentCompat.EXTRA_PACKAGE_NAME)?.also { pkg ->
			startActivity(buildTargetIntent(pkg, user, intent)) }
		finish()
	}

	private fun buildTargetIntent(pkg: String, user: UserHandle?, target: Intent): Intent {
		val caller = callingPackage; val pm = packageManager
		val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
		val detailsActivity = try { pm.queryIntentActivities(details, MATCH_SYSTEM_ONLY).getOrNull(0)?.activityInfo }
		catch (e: NullPointerException) { null.also { Log.e(TAG, "Error querying app detail activities: $details", e) }}    // Huawei-specific issue, only reported on Android 8
		var isCallerNotSettings = true      // Caller is not system Settings app
		val targetResolves by lazy { pm.queryIntentActivities(target, MATCH_DEFAULT_ONLY /* Excluding this activity */) }

		if (detailsActivity != null) {
			val referrer = referrer?.takeIf { it.scheme == "android-app" }?.authority
			isCallerNotSettings = referrer != CALLER_PLACEHOLDER_FOR_SETTINGS && caller != detailsActivity.packageName
			if (isCallerNotSettings && ! isCallerIslandButNotForwarder(caller)) {    // Started by 3rd-party app or this forwarder itself
				val intent = Intent(ACTION_SEARCH).putExtra(SearchManager.QUERY, "package:$pkg").setPackage(packageName)
				if (user != null) intent.putExtra(Intent.EXTRA_USER, user)
				pm.resolveActivity(intent, 0)?.also {
					return intent.setClassName(this, it.activityInfo.name) }}

			if (isCallerNotSettings && user != null && user != Users.current()) {
				if (user == Users.profile) details.component = ActivityShuttle.getForwarder(this) // Forwarding added in IslandProvisioning
				else details.setComponent(componentName).putExtra(Intent.EXTRA_USER, user)
			} else ActivityShuttle.forceNeverForwarding(pm, details)

			if (SDK_INT < O && isCallerNotSettings && targetResolves.all { it.activityInfo.packageName == "android" })
				return details } // No actual target, just simulate EXTRA_AUTO_LAUNCH_SINGLE_CHOICE on Android pre-O.

		val title = getString(R.string.app_info_forwarder_title, Apps.of(this).getAppName(pkg), pkg)
		val chooser = Intent.createChooser(target, title).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
		if (SDK_INT >= O) chooser.putExtra(IntentCompat.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, isCallerNotSettings)
		val initialIntents: MutableList<Intent> = ArrayList()
		if (isCallerNotSettings && detailsActivity != null) {
			if (user != null && ! Users.isOwner(user) && Users.isProfileManagedByIsland(user)) {    // Use mainland resolve to replace the misleading forwarding-resolved "Switch to work profile".
				val labelRes = detailsActivity.run { if (labelRes != 0) labelRes else applicationInfo.labelRes }
				initialIntents.add(LabeledIntent(details, detailsActivity.packageName, labelRes, detailsActivity.iconResource))
			} else initialIntents.add(details) }

		// Also add app markets to the chooser. EXTRA_ALTERNATE_INTENTS is not used here due to inability of de-dup. (e.g. Google Play Store)
		val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
		val excludes = ArrayList<ComponentName>()
		pm.queryIntentActivities(marketIntent, 0).forEach { resolve -> val activity = resolve.activityInfo
			if (activity.packageName == caller) return@forEach
			initialIntents.add(Intent(marketIntent).setClassName(activity.packageName, activity.name))
			targetResolves.map { it.activityInfo }.firstOrNull { activity.packageName == it.packageName && activity.labelRes == it.labelRes
					&& TextUtils.equals(activity.nonLocalizedLabel, it.nonLocalizedLabel) }?.also {
				excludes.add(ComponentName(it.packageName, it.name)) }}

		if (isCallerNotSettings && isCallerIslandButNotForwarder(caller))
			excludes.add(ComponentName(this, AppInfoForwarderActivity::class.java))

		return chooser.apply {
			if (excludes.isNotEmpty()) putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludes.toTypedArray())
			if (initialIntents.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray()) }
	}

	private fun isCallerIslandButNotForwarder(caller: String?): Boolean {
		return packageName == caller && intent.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT == 0
	}

	companion object {
		fun markAsLaunchedBySettings(intent: Intent) =
				intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$CALLER_PLACEHOLDER_FOR_SETTINGS"))
	}
}

private const val CALLER_PLACEHOLDER_FOR_SETTINGS = "settings"
private const val TAG = "Island.AIF"
