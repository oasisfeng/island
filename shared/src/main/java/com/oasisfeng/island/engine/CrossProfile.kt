package com.oasisfeng.island.engine

import android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
import android.content.*
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.ResolveInfo
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Users

class CrossProfile(private val context: Context) {

	/** The target activity must declare [CATEGORY_PARENT_PROFILE] and [Intent.CATEGORY_DEFAULT] in its intent-filter */
	fun startActivityInParentProfile(intent: Intent) {
		require(intent.data == null) { "Intent with data is not supported yet" }
		check(! Users.isOwner()) { "Must not be called in parent profile" }
		intent.addCategory(CATEGORY_PARENT_PROFILE)
		val candidates = queryActivities(intent)
		if (candidates.isEmpty()) throw ActivityNotFoundException("No matched activity for $intent")
		val forwarder = candidates.findForwarder()
				?: (addRequiredForwarding(intent).let { queryActivities(intent).findForwarder() }
						?: throw IllegalStateException("Failed to forward $intent"))
		context.startActivity(intent.setComponent(forwarder.activityInfo.run { ComponentName(packageName, name) }))
	}

	private fun addRequiredForwarding(intent: Intent) = DevicePolicies(context).addCrossProfileIntentFilter(
			IntentFilter(intent.action).apply { addCategory(CATEGORY_PARENT_PROFILE) }, FLAG_PARENT_CAN_ACCESS_MANAGED)

	private fun queryActivities(intent: Intent)
			= context.packageManager.queryIntentActivities(intent, MATCH_DISABLED_COMPONENTS or MATCH_DEFAULT_ONLY)    // Probably disabled in current profile

	private fun List<ResolveInfo>.findForwarder()
			= firstOrNull { it.activityInfo.packageName == "android" }//?.activityInfo?.run { ComponentName(packageName, name) }
}

private const val CATEGORY_PARENT_PROFILE = "com.oasisfeng.island.category.PARENT_PROFILE"
