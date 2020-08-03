package com.oasisfeng.island.controller

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INSTALLER_PACKAGE_NAME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.ui.WebContent
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.Config
import com.oasisfeng.island.analytics.Analytics
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.helper.installed
import com.oasisfeng.island.data.helper.isSystem
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.engine.common.WellKnownPackages
import com.oasisfeng.island.installer.InstallerExtras
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.interactive
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.RuntimeException
import java.util.concurrent.ExecutionException

/**
 * Controller for complex procedures of Island.
 *
 * Refactored by Oasis on 2018-9-30.
 */
object IslandAppClones {

	private const val CLONE_RESULT_ALREADY_CLONED = 0
	private const val CLONE_RESULT_OK_INSTALL = 1
	private const val CLONE_RESULT_OK_INSTALL_EXISTING = 2
	private const val CLONE_RESULT_OK_GOOGLE_PLAY = 10
	private const val CLONE_RESULT_UNKNOWN_SYS_MARKET = 11
	private const val CLONE_RESULT_NO_SYS_MARKET = -1

	@JvmStatic fun cloneApp(vm: BaseAndroidViewModel, source: IslandAppInfo, target: UserHandle) {
		vm.interactive(source.context()) { cloneApp(source, target) }
	}

	private suspend fun cloneApp(source: IslandAppInfo, target: UserHandle) {
		val context = source.context(); val pkg = source.packageName
		if (source.isSystem) {
			analytics().event("clone_sys").with(Analytics.Param.ITEM_ID, pkg).send()

			val enabled = Shuttle(context, to = target).invoke { DevicePolicies(this).enableSystemApp(pkg) } ?: return

			if (enabled) Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.label), Toast.LENGTH_SHORT).show()
			else Toast.makeText(context, context.getString(R.string.toast_cannot_clone, source.label), Toast.LENGTH_LONG).show()
			return
		}

		if (SDK_INT >= O && cloneAppViaRoot(context, source, target)) return    // Prefer root routine to avoid overhead (it's instant)

		val result = Shuttle(context, to = target).invoke(with = source as ApplicationInfo) { performAppCloningInProfile(this, it) }     // Cast to reduce the overhead
		Log.i(TAG, "Result of cloning $pkg to $target: $result")

		when (result) {
			CLONE_RESULT_OK_INSTALL ->          analytics().event("clone_install").with(Analytics.Param.ITEM_ID, pkg).send()
			CLONE_RESULT_OK_INSTALL_EXISTING -> analytics().event("clone_install_existing").with(Analytics.Param.ITEM_ID, pkg).send()
			CLONE_RESULT_OK_GOOGLE_PLAY ->      analytics().event("clone_via_play").with(Analytics.Param.ITEM_ID, pkg).send()
			CLONE_RESULT_ALREADY_CLONED ->      Toast.makeText(context, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show()
			CLONE_RESULT_NO_SYS_MARKET -> {
				val activity = Activities.findActivityFrom(context)
				if (activity != null) Dialogs.buildAlert(activity, 0, R.string.dialog_clone_incapable_explanation)
						.setNeutralButton(R.string.action_learn_more) { _,_ -> WebContent.view(context, Config.URL_FAQ.get()) }
						.setPositiveButton(android.R.string.cancel, null).show()
				else Toast.makeText(context, R.string.dialog_clone_incapable_explanation, Toast.LENGTH_LONG).show() }
			CLONE_RESULT_UNKNOWN_SYS_MARKET -> {
				val info = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).resolveActivityInfo(context.packageManager, 0)
				if (info != null && info.applicationInfo.isSystem)
					analytics().event("clone_via_market").with(Analytics.Param.ITEM_ID, pkg).with(Analytics.Param.ITEM_CATEGORY, info.packageName).send() }}
	}

	@OwnerUser @RequiresApi(O) private suspend fun cloneAppViaRoot(context: Context, source: IslandAppInfo, profile: UserHandle): Boolean {
		val pkg = source.packageName
		val cmd = "cmd package install-existing --user " + Users.toId(profile) + " " + pkg // Try root approach first
		val result = withContext(Dispatchers.Default) { Shell.SU.run(cmd) }
		try {
			val app = context.getSystemService(LauncherApps::class.java)!!.getApplicationInfo(pkg, 0, profile)
			if (app != null && app.installed) {
				Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.label), Toast.LENGTH_SHORT).show()
				return true
			}
		} catch (e: PackageManager.NameNotFoundException) {
			Log.i(TAG, "Failed to clone app via root: $pkg")
			if (result != null && result.isNotEmpty()) analytics().logAndReport(TAG, "Error executing: $cmd",
					ExecutionException("ROOT: " + cmd + ", result: " + java.lang.String.join(" \\n ", result), null))
		}
		return false
	}

	@JvmStatic @ProfileUser private fun performAppCloningInProfile(context: Context, appInfo: ApplicationInfo): Int {
		val pkg = appInfo.packageName; val policies = DevicePolicies(context)
		policies.clearUserRestrictionsIfNeeded(context, UserManager.DISALLOW_INSTALL_APPS)  // Blindly clear these restrictions

		if (SDK_INT >= P && policies.manager.isAffiliatedUser) try {
			if (policies.invoke(DevicePolicyManager::installExistingPackage, pkg))
				return CLONE_RESULT_OK_INSTALL_EXISTING
			Log.e(TAG, "Error cloning existent user app: $pkg")     // Fall-through
		} catch (e: RuntimeException) { analytics().logAndReport(TAG, "Error cloning existent user app: $pkg", e) } // Fall-through

		if (! IslandManager.ensureLegacyInstallNonMarketAppAllowed(context, policies)) {    // Fallback to install via app store
			val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(FLAG_ACTIVITY_NEW_TASK)
			policies.enableSystemAppByIntent(marketIntent)
			val marketApp = context.packageManager.resolveActivity(marketIntent, MATCH_SYSTEM_ONLY)?.activityInfo?.applicationInfo
			return when {
				marketApp == null || ! marketApp.isSystem -> CLONE_RESULT_NO_SYS_MARKET
				marketApp.packageName != WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE -> CLONE_RESULT_UNKNOWN_SYS_MARKET.also {
					context.startActivity(marketIntent) }
				else -> CLONE_RESULT_OK_GOOGLE_PLAY.also {
					policies.enableSystemApp(WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES)     // Special dependency
					context.startActivity(marketIntent) }}}

		@Suppress("DEPRECATION") val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
				.putExtra(EXTRA_INSTALLER_PACKAGE_NAME, context.packageName).addFlags(FLAG_ACTIVITY_NEW_TASK)
				.putExtra(InstallerExtras.EXTRA_APP_INFO, appInfo).addCategory(context.packageName) // Launch App Installer
		policies.enableSystemAppByIntent(intent)
		context.startActivity(intent)
		return CLONE_RESULT_OK_INSTALL
	}

	private const val TAG = "Island.IC"
}