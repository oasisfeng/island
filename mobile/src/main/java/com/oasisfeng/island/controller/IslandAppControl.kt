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
import androidx.lifecycle.viewModelScope
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.helper.AppStateTrackingHelper
import com.oasisfeng.island.engine.ClonedHiddenSystemApps.Companion.setCloned
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.interactive
import com.oasisfeng.island.model.interactiveFuture
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import org.jetbrains.annotations.NotNull

object IslandAppControl {

	@JvmStatic fun requestRemoval(vm: BaseAndroidViewModel, activity: Activity, app: IslandAppInfo) = vm.interactive(app.context()) {
		analytics().event("action_uninstall").with(ITEM_ID, app.packageName).with(ITEM_CATEGORY, "system").send()

		if (! unfreezeIfNeeded(app)) return@interactive

		if (app.isSystem) {
			analytics().event("action_disable_sys_app").with(ITEM_ID, app.packageName).send()
			if (app.isCritical) {
				Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.dialog_critical_app_warning).withCancelButton()
						.setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(vm, app) }.show()
			} else Dialogs.buildAlert(activity, 0, R.string.prompt_disable_sys_app_as_removal).withCancelButton()
					.setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(vm, app) }.show()
		} else {
			Activities.startActivity(activity, Intent(Intent.ACTION_UNINSTALL_PACKAGE)
					.setData(Uri.fromParts("package", app.packageName, null)).putExtra(Intent.EXTRA_USER, app.user))
			if (! Users.isProfileRunning(activity, app.user)) {  // App clone can actually be removed in quiet mode, without callback triggered.
				if (! activity.isDestroyed) AppStateTrackingHelper.requestSyncWhenResumed(activity, app.packageName, app.user)
			}
		}
	}

	@JvmStatic fun launch(context: Context, vm: BaseAndroidViewModel, app: IslandAppInfo) {
		val pkg = app.packageName
		analytics().event("action_launch").with(ITEM_ID, pkg).send()

		if (! app.isHidden) {        // Not frozen, launch the app directly. TODO: If isBlocked() ?
			if (! IslandManager.launchApp(context, pkg, app.user))
				Toast.makeText(context, context.getString(R.string.toast_app_launch_failure, app.label), Toast.LENGTH_SHORT).show()
			return }

		vm.interactive(context) {
			var failure = Shuttle(context, to = app.user).invoke(pkg, IslandManager::ensureAppFreeToLaunch)

			if (failure == null) if (! IslandManager.launchApp(context, pkg, app.user)) failure = "launcher_activity_not_found"
			if (failure != null) {
				Toast.makeText(context, R.string.toast_failed_to_launch_app, Toast.LENGTH_LONG).show()
				analytics().event("app_launch_error").with(ITEM_ID, pkg).with(ITEM_CATEGORY, "launcher_activity_not_found").send() }}
	}

	private suspend fun launchSystemAppSettings(app: IslandAppInfo) {   // Stock app info activity requires the target app not hidden.
		if (unfreezeIfNeeded(app))
			app.context().getSystemService(LauncherApps::class.java)!!.startAppDetailsActivity(ComponentName(app.packageName, ""), app.user, null, null)
	}
	@JvmStatic fun launchSystemAppSettings(vm: BaseAndroidViewModel, app: IslandAppInfo)
			= vm.interactive(app.context()) { launchSystemAppSettings(app) }

	@JvmStatic fun launchExternalAppSettings(vm: BaseAndroidViewModel, app: @NotNull IslandAppInfo) {
		val context = app.context()
		val intent = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setPackage(context.packageName)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, app.packageName).putExtra(Intent.EXTRA_USER, app.user)
		val resolve = context.packageManager.resolveActivity(intent, 0) ?: return
		// Should never happen as module "installer" is always bundled with "mobile".
		intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
		vm.interactive(context) { if (unfreezeIfNeeded(app)) Activities.startActivity(context, intent) }
	}

	private suspend fun unfreezeIfNeeded(app: IslandAppInfo): Boolean {
		return if (app.isHidden) unfreeze(app) else true
	}

	private suspend fun freeze(app: IslandAppInfo): Boolean {
		val frozen = Shuttle(app.context(), to = app.user).invoke(app.packageName) { ensureAppHiddenState(this, it, true) }
		if (frozen && app.isSystem) stopTreatingHiddenSysAppAsDisabled(app)
		return frozen
	}

	@JvmStatic fun freeze(vm: BaseAndroidViewModel, app: IslandAppInfo) = vm.interactiveFuture(app.context()) { freeze(app) }

	private suspend fun unfreeze(app: IslandAppInfo)
			= unfreeze(app.context(), app.user, app.packageName)
	suspend fun unfreeze(context: Context, profile: UserHandle, pkg: String)
			= Shuttle(context, to = profile).invoke(pkg) { ensureAppHiddenState(this, it, false) }
	@JvmStatic fun unfreeze(vm: BaseAndroidViewModel, app: IslandAppInfo) = vm.interactiveFuture(app.context()) { unfreeze(app) }

	@OwnerUser @ProfileUser private fun ensureAppHiddenState(context: Context, pkg: String, hidden: Boolean): Boolean {
		val policies = DevicePolicies(context)
		if (policies.setApplicationHidden(pkg, hidden)) return true
		// Since setApplicationHidden() return false if already in that state, also check the current state.
		val state = policies.invoke(DevicePolicyManager::isApplicationHidden, pkg)
		if (hidden == state) return true
		val activeAdmins = policies.manager.activeAdmins
		if (activeAdmins != null && activeAdmins.any { pkg == it.packageName })
			Toasts.showLong(context, R.string.toast_error_freezing_active_admin) // TODO: Action to open device-admin settings.
		else Toasts.showLong(context, R.string.toast_error_freeze_failure)
		return false
	}

	@JvmStatic fun setSuspended(vm: BaseAndroidViewModel, app: IslandAppInfo, suspended: Boolean)
			= Shuttle(app.context(), to = app.user).future(vm.viewModelScope, app.packageName) { pkg ->
				setPackageSuspended(this, pkg, suspended) }

	private fun setPackageSuspended(context: Context, pkg: String, suspended: Boolean)
			= setPackagesSuspended(context, arrayOf(pkg), suspended).isEmpty()
	fun setPackagesSuspended(context: Context, pkgs: Array<String>, suspended: Boolean): Array<String>
			= DevicePolicies(context).invoke(DevicePolicyManager::setPackagesSuspended, pkgs, suspended)

	@JvmStatic fun unfreezeInitiallyFrozenSystemApp(vm: BaseAndroidViewModel, app: IslandAppInfo) = vm.interactiveFuture(app.context()) {
		Shuttle(app.context(), to = app.user).invoke(app.packageName) { pkg -> IslandManager.ensureAppHiddenState(this, pkg, false) }.also {
			if (it) stopTreatingHiddenSysAppAsDisabled(app) }}

	private suspend fun stopTreatingHiddenSysAppAsDisabled(app: IslandAppInfo)
			= Shuttle(app.context(), to = app.user).invoke(app.packageName, ::setCloned)
}
