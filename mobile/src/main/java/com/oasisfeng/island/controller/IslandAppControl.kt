package com.oasisfeng.island.controller

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.widget.Toast
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.util.Apps
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.helper.AppStateTrackingHelper
import com.oasisfeng.island.engine.ClonedHiddenSystemApps
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.interactive
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import org.jetbrains.annotations.NotNull

object IslandAppControl {

	@JvmStatic fun requestRemoval(activity: Activity, app: IslandAppInfo) {
		analytics().event("action_uninstall").with(ITEM_ID, app.packageName).with(ITEM_CATEGORY, "system").send()

		if (! unfreezeIfNeeded(app)) return

		if (app.isSystem) {
			analytics().event("action_disable_sys_app").with(ITEM_ID, app.packageName).send()
			if (app.isCritical) Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.dialog_critical_app_warning)
					.withCancelButton().setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(app) }.show()
			else Dialogs.buildAlert(activity, 0, R.string.prompt_disable_sys_app_as_removal)
					.withCancelButton().setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(app) }.show() }
		else {
			Activities.startActivity(activity, Intent(Intent.ACTION_UNINSTALL_PACKAGE)
					.setData(Uri.fromParts("package", app.packageName, null)).putExtra(Intent.EXTRA_USER, app.user))
			if (! Users.isProfileRunning(activity, app.user))   // App clone can actually be removed in quiet mode, without callback triggered.
				if (! activity.isDestroyed) AppStateTrackingHelper.requestSyncWhenResumed(activity, app.packageName, app.user) }
	}

	@JvmStatic fun launch(context: Context, app: IslandAppInfo) {
		analytics().event("action_launch").with(ITEM_ID, app.packageName).send()

		if (app.isHidden) unfreezeAndLaunch(context, app)
		else if (! IslandManager.launchApp(context, app.packageName, app.user))      // Not frozen, launch the app directly. TODO: If isBlocked() ?
			Toast.makeText(context, context.getString(R.string.toast_app_launch_failure, app.label), Toast.LENGTH_SHORT).show()
	}

	private fun unfreezeAndLaunch(context: Context, app: IslandAppInfo) {
		val pkg = app.packageName
		val failure = Shuttle(context, to = app.user).invokeNoThrows(with = pkg) { IslandManager.ensureAppFreeToLaunch(this, it) }

		if (failure == null) return Toast.makeText(context, R.string.prompt_island_not_ready, Toast.LENGTH_LONG).show()
		else if (failure.isNotEmpty()) {
			Toast.makeText(context, R.string.toast_app_launch_error, Toast.LENGTH_LONG).show()
			return analytics().event("app_launch_error").with(ITEM_ID, pkg).with(ITEM_CATEGORY, failure).send() }

		if (! IslandManager.launchApp(context, pkg, app.user)) {
			Toast.makeText(context, context.getString(R.string.toast_app_launch_failure, Apps.of(context).getAppName(pkg)), Toast.LENGTH_LONG).show()
			analytics().event("app_launch_error").with(ITEM_ID, pkg).with(ITEM_CATEGORY, "launcher_activity_not_found").send() }
	}

	@JvmStatic fun launchSystemAppSettings(app: IslandAppInfo) {    // Stock app info activity requires the target app not hidden.
		if (unfreezeIfNeeded(app))
			app.context().getSystemService(LauncherApps::class.java)!!.startAppDetailsActivity(ComponentName(app.packageName, ""), app.user, null, null)
	}

	@JvmStatic fun launchExternalAppSettings(vm: BaseAndroidViewModel, app: @NotNull IslandAppInfo) {
		val context = app.context()
		val intent = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setPackage(context.packageName)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, app.packageName).putExtra(Intent.EXTRA_USER, app.user)
		val resolve = context.packageManager.resolveActivity(intent, 0) ?: return
		// Should never happen as module "installer" is always bundled with "mobile".
		intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
		vm.interactive(context) { if (unfreezeIfNeeded(app)) Activities.startActivity(context, intent) }
	}

	private fun unfreezeIfNeeded(app: IslandAppInfo): Boolean {
		return if (! app.isHidden) true else unfreeze(app)
			?: false.also { Toasts.showLong(app.context(), R.string.prompt_island_not_ready) }
	}

	@JvmStatic fun freeze(app: IslandAppInfo): Boolean {
		val frozen = Shuttle(app.context(), to = app.user).invoke(with = app.packageName) {
			ensureAppHiddenState(this, it, true) }
		if (frozen && app.isSystem) stopTreatingHiddenSysAppAsDisabled(app)
		return frozen
	}

	@JvmStatic fun unfreeze(app: IslandAppInfo) = unfreeze(app.context(), app.user, app.packageName)
	private fun unfreeze(context: Context, profile: UserHandle, pkg: String)
			= Shuttle(context, to = profile).invokeNoThrows { ensureAppHiddenState(this, pkg, false) }

	@OwnerUser @ProfileUser private fun ensureAppHiddenState(context: Context, pkg: String, hidden: Boolean): Boolean {
		val policies = DevicePolicies(context)
		if (policies.setApplicationHidden(pkg, hidden)) return true
		// Since setApplicationHidden() return false if already in that state, also check the current state.
		val state = policies.invoke(DevicePolicyManager::isApplicationHidden, pkg)
		if (hidden == state) return true
		val activeAdmins = policies.manager.activeAdmins
		if (activeAdmins != null && activeAdmins.any { pkg == it.packageName })
			Toasts.showLong(context, R.string.toast_error_freezing_active_admin) // TODO: Action to open device-admin settings.
		else Toasts.showLong(context, if (hidden) R.string.toast_error_freeze_failure else R.string.toast_error_unfreeze_failure)
		return false
	}

	@JvmStatic fun setSuspended(app: IslandAppInfo, suspended: Boolean) =
		Shuttle(app.context(), to = app.user).invoke(with = app.packageName) { setPackageSuspended(this, it, suspended) }
	private fun setPackageSuspended(context: Context, pkg: String, suspended: Boolean)
			= setPackagesSuspended(context, arrayOf(pkg), suspended).isEmpty()
	fun setPackagesSuspended(context: Context, pkgs: Array<String>, suspended: Boolean): Array<String>
			= DevicePolicies(context).invoke(DevicePolicyManager::setPackagesSuspended, pkgs, suspended)

	@JvmStatic fun unfreezeInitiallyFrozenSystemApp(app: IslandAppInfo) =
		Shuttle(app.context(), to = app.user).invoke(with = app.packageName) { IslandManager.ensureAppHiddenState(this, it, false) }.also {
			if (it) stopTreatingHiddenSysAppAsDisabled(app) }

	private fun stopTreatingHiddenSysAppAsDisabled(app: IslandAppInfo)
			= Shuttle(app.context(), to = app.user).invoke(with = app.packageName) { ClonedHiddenSystemApps.setCloned(this, it) }
}
