package com.oasisfeng.island

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.DELEGATION_PACKAGE_ACCESS
import android.content.ComponentName
import android.content.Context
import android.content.Context.RESTRICTIONS_SERVICE
import android.content.Intent
import android.content.Intent.EXTRA_USER
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.RestrictionsManager
import android.content.RestrictionsManager.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.content.pm.PackageManager.GET_META_DATA
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.PersistableBundle
import android.os.UserHandle
import android.service.restrictions.RestrictionsReceiver
import android.util.Log
import android.widget.Toast
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.notification.NotificationIds
import com.oasisfeng.island.open.BuildConfig
import com.oasisfeng.island.open.R
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.pattern.PseudoContentProvider

/**
 * Created by Oasis on 2019-6-8.
 */
const val ACTION_AUTHORIZE = "AUTHORIZE"
const val ACTION_REFUSE = "REFUSE"

class DelegatedScopeAuthorization : RestrictionsReceiver() {

    override fun onRequestPermission(context: Context, pkg: String?, requestType: String?, requestId: String?, request: PersistableBundle?) {
        if (pkg.isNullOrEmpty()) return logAndToast(context, pkg, "Missing request package")
        if (requestId.isNullOrEmpty()) return logAndToast(context, pkg, "Missing request ID")
        val delegation = request?.getString(REQUEST_KEY_DATA)
        if (delegation.isNullOrEmpty()) return logAndToast(context, pkg, "Missing delegation (REQUEST_KEY_DATA) in request")
        val user = UserHandles.of(request.getInt(ApiConstants.REQUEST_KEY_USER_SERIAL_NUMBER, -2))

        if (BuildConfig.DEBUG && requestType == "-" + ApiConstants.TYPE_DELEGATION)
            return DelegationManager.removeAuthorizedDelegation(DevicePolicies(context), pkg, user, delegation)
        if (requestType != ApiConstants.TYPE_DELEGATION) return logAndToast(context, pkg, "Unsupported request type: $requestType")

        // Use meta-data instead of restrictions XML declaration, to avoid declared restriction being unintentionally recognized by other DPC.
        val declaredDelegations = context.packageManager.getApplicationInfo(pkg, GET_META_DATA)
                .metaData?.getString(ApiConstants.TYPE_DELEGATION) ?: ""
        if (! declaredDelegations.split(',').contains(delegation))
            return logAndToast(context, pkg, "delegation is not declared in meta-data")
        val delegationWithLabel = getSupportedDelegatedScope(delegation)
                ?: return logAndToast(context, pkg, "Unsupported delegation (specified by requestId): $delegation")

        if (DelegationManager.isDelegationAuthorized(DevicePolicies(context), pkg, user, delegation))
            return notifyAuthorizationResult(context, pkg, requestId, RESULT_APPROVED)

        if (NotificationIds.Authorization.isBlocked(context)) {
            context.startActivity(NotificationIds.Authorization.buildChannelSettingsIntent(context).addFlags(FLAG_ACTIVITY_NEW_TASK))
            return Toast.makeText(context, R.string.prompt_unblock_notification_for_auth_request, Toast.LENGTH_LONG).show()
        }
        val intent = Intent(context, javaClass).setData(Uri.parse("request:$requestId"))
                .putExtra(EXTRA_PACKAGE_NAME, pkg).putExtra(EXTRA_USER, user).putExtra(REQUEST_KEY_DATA, delegation)
        val authorize = PendingIntent.getBroadcast(context, 0, intent.setAction(ACTION_AUTHORIZE), FLAG_UPDATE_CURRENT)
        val refuse = PendingIntent.getBroadcast(context, 0, intent.setAction(ACTION_REFUSE), FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        NotificationIds.Authorization.post(context, requestId, Notification.Builder(context).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(context.resources.getColor(R.color.accent)).setStyle(Notification.BigTextStyle())
                .setContentTitle(context.getString(R.string.notification_delegated_scope_auth_title, Apps.of(context).getAppName(pkg) ?: pkg))
                .setContentText(context.getString(R.string.notification_delegated_scope_auth_text, context.getText(delegationWithLabel.second)))
                .setDeleteIntent(refuse).addAction(Notification.Action.Builder(0, context.getText(R.string.action_authorize), authorize).build()))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val authorized = when (intent.action) {
            ACTION_AUTHORIZE -> true
            ACTION_REFUSE -> false
            else -> return super.onReceive(context, intent) }
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val user : UserHandle = intent.getParcelableExtra(EXTRA_USER) ?: return
        val delegation = intent.getStringExtra(REQUEST_KEY_DATA) ?: return
        val requestId = intent.data?.schemeSpecificPart
        onRequestReactedByUser(context, authorized, requestId, pkg, user, delegation)
    }

    private fun onRequestReactedByUser(context: Context, authorized: Boolean, requestId: String?, pkg: String, user: UserHandle, delegation: String) {
        notifyAuthorizationResult(context, pkg, requestId, if (authorized) RESULT_APPROVED else RESULT_DENIED)
        if (authorized) DelegationManager.addAuthorizedDelegation(DevicePolicies(context), pkg, user, delegation)
        NotificationIds.Authorization.cancel(context, requestId)
    }

    private fun notifyAuthorizationResult(context: Context, pkg: String, requestId: String?, result: Int) {
        (context.getSystemService(RESTRICTIONS_SERVICE) as RestrictionsManager).notifyPermissionResponse(pkg,
                PersistableBundle(2).apply { putString(REQUEST_KEY_ID, requestId); putInt(RESPONSE_KEY_RESULT, result) })
    }

    private fun getSupportedDelegatedScope(delegation: String): Pair<String, Int>? {
        return delegation to when (delegation) {
            ApiConstants.DELEGATION_PACKAGE_ACCESS ->   R.string.label_delegation_package_access
            ApiConstants.DELEGATION_PERMISSION_GRANT -> R.string.label_delegation_permission_grant
            ApiConstants.DELEGATION_APP_OPS ->          R.string.label_delegation_app_ops
            else -> return null
        }
    }

    private fun logAndToast(context: Context?, pkg: String?, message: String) {
        Log.w(TAG, message)
        pkg?.let { Apps.of(context).getAppInfo(it) }?.flags?.apply { and(ApplicationInfo.FLAG_TEST_ONLY) != 0 }?.also {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    class Initializer : PseudoContentProvider() { override fun onCreate(): Boolean {
        val context = context(); val policies = DevicePolicies(context)
        if (policies.isProfileOrDeviceOwnerOnCallingUser) try {
            policies.execute(DevicePolicyManager::setRestrictionsProvider, ComponentName(context, DelegatedScopeAuthorization::class.java))
            // This allows us (thus API caller) to call DevicePolicyManager APIs with null as admin component argument.
            if (SDK_INT >= O) policies.execute(DevicePolicyManager::setDelegatedScopes, context.packageName, listOf(DELEGATION_PACKAGE_ACCESS))
            context.packageManager.setComponentEnabledSetting(ComponentName(context, javaClass), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error initializing", e)
        }
        return false
    }}
}

private const val TAG = "Island.DSA"