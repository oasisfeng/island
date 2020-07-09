package com.oasisfeng.island.controller

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.widget.Toast
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.content.IntentCompat
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY
import com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.helper.AppStateTrackingHelper
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.shuttle.MethodShuttle
import com.oasisfeng.island.shuttle.MethodShuttle.GeneralMethod
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

object IslandAppControl {

	@JvmStatic fun requestRemoval(context: Context, app: IslandAppInfo) {
		analytics().event("action_uninstall").with(ITEM_ID, app.packageName).with(ITEM_CATEGORY, "system").send()
		unfreezeIfNeeded(context, app).thenAccept {
			if (app.isSystem) {
				analytics().event("action_disable_sys_app").with(ITEM_ID, app.packageName).send()
				val activity = Activities.findActivityFrom(context)!!
				if (app.isCritical) {
					Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.dialog_critical_app_warning).withCancelButton()
							.setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(context, app) }.show()
				} else Dialogs.buildAlert(activity, 0, R.string.prompt_disable_sys_app_as_removal).withCancelButton()
						.setPositiveButton(R.string.action_continue) { _,_ -> launchSystemAppSettings(context, app) }.show()
			} else {
				Activities.startActivity(context, Intent(Intent.ACTION_UNINSTALL_PACKAGE)
						.setData(Uri.fromParts("package", app.packageName, null)).putExtra(Intent.EXTRA_USER, app.user))
				if (! Users.isProfileRunning(context, app.user)) {  // App clone can actually be removed in quiet mode, without callback triggered.
					val activity = Activities.findActivityFrom(context)
					if (activity != null) AppStateTrackingHelper.requestSyncWhenResumed(activity, app.packageName, app.user)
				}
			}
		}
	}

	@JvmStatic fun launch(context: Context, app: IslandAppInfo) {
		val pkg = app.packageName
		analytics().event("action_launch").with(ITEM_ID, pkg).send()
		if (! app.isHidden) {        // Not frozen, launch the app directly. TODO: If isBlocked() ?
			if (! IslandManager.launchApp(context, pkg, app.user))
				Toast.makeText(context, context.getString(R.string.toast_app_launch_failure, app.label), Toast.LENGTH_SHORT).show()
		} else {
			(if (Users.isOwner(app.user))
				CompletableFuture.completedFuture(IslandManager.ensureAppFreeToLaunch(context, pkg))
			else MethodShuttle.runInProfile(context) @Suppress("NAME_SHADOWING") {
				context -> IslandManager.ensureAppFreeToLaunch(context, pkg)
			}).thenAccept { result ->
				var failure = result
				if (failure == null) if (! IslandManager.launchApp(context, pkg, app.user)) failure = "launcher_activity_not_found"
				if (failure != null) {
					Toast.makeText(context, R.string.toast_failed_to_launch_app, Toast.LENGTH_LONG).show()
					analytics().event("app_launch_error").with(ITEM_ID, pkg).with(ITEM_CATEGORY, "launcher_activity_not_found").send()
				}
			}.exceptionally { t -> null.also {
				reportAndToastForInternalException(context, "Error unfreezing and launching app: $pkg", t) }}
		}
	}

	@JvmStatic fun launchSystemAppSettings(context: Context, app: IslandAppInfo) {
		// Stock app info activity requires the target app not hidden.
		unfreezeIfNeeded(context, app).thenAccept { unfrozen ->
			if (unfrozen!!) Objects.requireNonNull(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
					.startAppDetailsActivity(ComponentName(app.packageName, ""), app.user, null, null)
		}
	}

	@JvmStatic fun launchExternalAppSettings(context: Context, app: IslandAppInfo) {
		val intent = Intent(IntentCompat.ACTION_SHOW_APP_INFO).setPackage(context.packageName)
				.putExtra(IntentCompat.EXTRA_PACKAGE_NAME, app.packageName).putExtra(Intent.EXTRA_USER, app.user)
		val resolve = context.packageManager.resolveActivity(intent, 0) ?: return
		// Should never happen as module "installer" is always bundled with "mobile".
		intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
		unfreezeIfNeeded(context, app).thenAccept { Activities.startActivity(context, intent) }
	}

	private fun unfreezeIfNeeded(context: Context, app: IslandAppInfo): CompletionStage<Boolean> {
		return if (app.isHidden) unfreeze(context, app) else CompletableFuture.completedFuture<Boolean>(true)
	}

	@JvmStatic fun unfreeze(context: Context, app: IslandAppInfo): CompletionStage<Boolean> {
		val pkg = app.packageName
		return if (Users.isOwner(app.user)) CompletableFuture.completedFuture(ensureAppHiddenState(context, pkg, false))
		else MethodShuttle.runInProfile(context, GeneralMethod {
			ensureAppHiddenState(context, pkg, false)
		}).exceptionally { t -> false.also {
			reportAndToastForInternalException(context, "Error unfreezing app: " + app.packageName, t) }}
	}

	@OwnerUser @ProfileUser @JvmStatic fun ensureAppHiddenState(context: Context, pkg: String, hidden: Boolean): Boolean {
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

	private fun reportAndToastForInternalException(context: Context, log: String, t: Throwable) {
		analytics().logAndReport(TAG, log, t)
		Toast.makeText(context, "Internal error: " + t.message, Toast.LENGTH_LONG).show()       // TODO: Refine
	}
}

private const val TAG = "Island.AC"