package com.oasisfeng.island.settings

import android.app.Activity
import android.app.AppOpsManager
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.AsyncTask
import android.os.Build.VERSION_CODES.P
import android.os.Process
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.util.Apps
import com.oasisfeng.android.util.SafeAsyncTask
import com.oasisfeng.island.appops.AppOpsHelper
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.util.Hacks

@RequiresApi(P) class OpsManager(private val activity: Activity, private val permission: String, private val op: Int) {

	internal fun startOpsManager(prompt: Int) {
		val progress = Dialogs.buildProgress(activity, R.string.prompt_appops_loading).indeterminate().onCancel { mCanceled = true }.start()
		SafeAsyncTask.execute(activity, { buildSortedAppList() }, { activity, apps: List<AppInfoWithOps>? ->
			if (mCanceled) return@execute
			progress.dismiss()
			if (apps == null) return@execute
			if (apps.isNotEmpty()) show(apps, prompt)
			else Dialogs.buildAlert(activity, 0, R.string.prompt_appops_no_such_apps).setPositiveButton(R.string.action_done, null).show()
		})
	}

	private fun show(apps: List<AppInfoWithOps>, prompt: Int) {
		val checkedItems = BooleanArray(apps.size) { i -> ! apps[i].mRevoked }
		Dialogs.buildCheckList(activity, activity.getString(prompt), apps.map { it.mLabel }.toTypedArray(), checkedItems) { _, which, checked ->
			apps[which].also { if (checked) it.restore() else it.revoke() }
		}.setNeutralButton(R.string.action_revoke_all) { _, _ ->
			Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.prompt_appops_revoke_for_all_users_apps)
					.withOkButton { apps.forEach { if (! it.mSystem) it.revoke() } }
					.withCancelButton().show()
		}.setPositiveButton(R.string.action_done) { _,_ -> AsyncTask.execute {
			syncPermissionsLockedStateForApps(op, apps.map { it.pkg }) }    // To migrate legacy ops without permission lock
		}.show()
	}

	private fun syncPermissionsLockedStateForApps(op: Int, pkgs: List<String>) {
		mAppOps.getPackageOps(op).forEach { (pkg, pkgOps) ->
			if (pkg in pkgs) mAppOps.syncPermissionLockedState(pkg, op, pkgOps.ops?.getOrNull(0)?.mode ?: return@forEach) }
	}

	/** Revoked first, granted & denied first (unused last), system apps last (user apps first), then by label */
	private fun buildSortedAppList(): List<AppInfoWithOps>? {   // null if canceled
		val entries = HashMap<String, AppInfoWithOps>()
		// Apps with permission granted
		activity.packageManager.getPackagesHoldingPermissions(arrayOf(permission), 0).forEach {
			if (isUserAppOrUpdatedNonPrivilegeSystemApp(it.applicationInfo))
				entries[it.packageName] = AppInfoWithOps(it.applicationInfo, it.packageName !in mOpsRevokedPkgs) }
		if (mCanceled) return null
		// Frozen apps and apps with explicit app-op revoked
		val apps = activity.packageManager.getInstalledPackages(GET_PERMISSIONS or MATCH_UNINSTALLED_PACKAGES)
		if (mCanceled) return null
		apps.forEach {
			val pkg = it.packageName; val app = it.applicationInfo
			if (pkg !in entries && Apps.isInstalledInCurrentUser(app) && isUserAppOrUpdatedNonPrivilegeSystemApp(app)
					&& (pkg in mOpsRevokedPkgs || it.requestedPermissions?.contains(permission) == true)) {
				if (mCanceled) return null
				entries[pkg] = AppInfoWithOps(app, false) }}
		return entries.values.sortedWith(compareBy({ ! it.mRevoked }, { ! it.mGranted }, { it.mSystem }, { it.mLabel }))
	}

	private fun isUserAppOrUpdatedNonPrivilegeSystemApp(app: ApplicationInfo)   // Limited to "updated" to filter out unwanted system apps but still keep possible bloatware
			= (app.flags and FLAG_SYSTEM == 0 || (app.flags and FLAG_UPDATED_SYSTEM_APP != 0 && ! Apps.isPrivileged(app)))
			&& UserHandles.getAppId(app.uid) != mAppId

	private inner class AppInfoWithOps(private val info: ApplicationInfo, val mGranted: Boolean) {

		fun revoke() = mAppOps.revokeAndLockPermission(pkg, op, info.uid).showToastIfFalse()
		fun restore() = mAppOps.restoreAndUnlockPermission(pkg, op, info.uid).showToastIfFalse()

		private fun Boolean.showToastIfFalse() {
			if (! this) Toast.makeText(activity, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_LONG).show()
		}

		val mLabel by lazy {
			StringBuilder().apply {
				if (mSystem) append(mSystemPrefix).append(' ')
				append(mAppsHelper.getAppName(info))
				if (mGranted) append(' ').append(mGrantedSuffix)
				if (info.hidden) append(' ').append(mHiddenSuffix)
			}.toString()
		}

		val pkg: String = info.packageName
		val mRevoked = mOpsRevokedPkgs.contains(pkg)

		internal val mSystem = Apps.isSystem(info)
	}

	private val mAppsHelper = Apps.of(activity)
	private val mAppOps = AppOpsHelper(activity)
	private val mOpsRevokedPkgs by lazy { mAppOps.getPackageOps(op).mapNotNull { entry -> entry.key.takeIf { isOpRevoked(entry.value) }}}
	private val mAppId = UserHandles.getAppId(Process.myUid())
	private var mCanceled = false

	private val mSystemPrefix = activity.getString(R.string.label_prefix_for_system_app)
	private val mGrantedSuffix = activity.getString(R.string.label_suffix_permission_granted)
	private val mHiddenSuffix = activity.getString(R.string.default_launch_shortcut_prefix).trimEnd()

	companion object {

		fun isOpRevoked(pkgOps: Hacks.AppOpsManager.PackageOps)
				= pkgOps.ops?.getOrNull(0)?.mode ?: AppOpsManager.MODE_ALLOWED != AppOpsManager.MODE_ALLOWED
	}
}

private const val TAG = "Island.OM"
