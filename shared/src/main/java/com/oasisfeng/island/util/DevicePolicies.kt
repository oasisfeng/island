package com.oasisfeng.island.util

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N_MR1
import android.os.Build.VERSION_CODES.P
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import com.oasisfeng.android.content.pm.LauncherAppsCompat
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.appops.AppOpsCompat
import com.oasisfeng.island.appops.AppOpsHelper
import com.oasisfeng.island.shared.R
import java.util.*
import java.util.function.BiConsumer

typealias DPM = DevicePolicyManager

/**
 * Utility to ease the use of [android.app.admin.DevicePolicyManager]
 *
 * Created by Oasis on 2016/6/14.
 */
class DevicePolicies {

    val isProfileOwner: Boolean
        get() = manager.isProfileOwnerApp(Modules.MODULE_ENGINE)

    val isProfileOrDeviceOwnerOnCallingUser: Boolean
        get() = isProfileOwner || Users.isParentProfile() && isActiveDeviceOwner

    val isActiveDeviceOwner: Boolean
        @OwnerUser get() = manager.isAdminActive(sAdmin) && manager.isDeviceOwnerApp(sAdmin.packageName)   // Fall-back check, only if we are the device owner.

    var isBackupServiceEnabled: Boolean
        /** @see DevicePolicyManager.isBackupServiceEnabled */ // Hidden on Android 7.1.x
        @SuppressLint("NewApi") get() = manager.isBackupServiceEnabled(sAdmin)
        @RequiresApi(N_MR1) @SuppressLint("NewApi") set(enabled) { manager.setBackupServiceEnabled(sAdmin, enabled) }

    /** @return the package name of current device owner, null if none or empty string if unknown. */
    @OwnerUser fun getDeviceOwner(): String? = Hacks.DevicePolicyManager_getDeviceOwner.let { when {
        ! it.isAbsent -> it.invoke().on(manager)
        isActiveDeviceOwner -> sAdmin.packageName
        else -> ""
    }}

    /** @return true if successfully enabled, false if package not found or not system app.
     *  @see DevicePolicyManager.enableSystemApp */
    fun enableSystemApp(pkg: String): Boolean =
        try { manager.enableSystemApp(sAdmin, pkg); true }
        catch (e: IllegalArgumentException) { false }   // When package is not present on this device.

    /** @see DevicePolicyManager.enableSystemApp */
    fun enableSystemAppByIntent(intent: Intent): Boolean = manager.enableSystemApp(sAdmin, intent) > 0

    fun addUserRestrictionIfNeeded(key: String) {
        if (Users.isProfileManagedByIsland() && UserManager.DISALLOW_SET_WALLPAPER == key) return // Immutable
        if (! manager.getUserRestrictions(sAdmin).containsKey(key))
            manager.addUserRestriction(sAdmin, key)
    }

    fun clearUserRestrictionsIfNeeded(key: String) {
        if (Users.isProfileManagedByIsland() && UserManager.DISALLOW_SET_WALLPAPER == key) return // Immutable
        if (manager.getUserRestrictions(sAdmin).containsKey(key))
            manager.clearUserRestriction(sAdmin, key)
    }

    /** @see DevicePolicyManager.isPackageSuspended */
    @Throws(NameNotFoundException::class) fun isPackageSuspended(pkg: String) = manager.isPackageSuspended(sAdmin, pkg) // Helper due to exception

    /** @see DevicePolicyManager.addCrossProfileIntentFilter */
    fun addCrossProfileIntentFilter(filter: IntentFilter, flags: Int) = // Helper for IntentFilters, which may throw.
        manager.addCrossProfileIntentFilter(sAdmin, filter, flags)

    fun setApplicationHidden(pkg: String, hidden: Boolean): Boolean {
        if (SDK_INT >= P && hidden && Permissions.has(mAppContext, AppOpsCompat.GET_APP_OPS_STATS))
            try { AppOpsHelper(mAppContext).saveAppOps(pkg) } // Without GET_APP_OPS_STATS, app-op is saved upon change.
            catch (e: Exception) {
                Toasts.showLong(mAppContext, R.string.prompt_failed_preserving_app_ops)
                analytics().logAndReport(TAG, "Error saving app ops settings for $pkg", e) }

        return setApplicationHiddenWithoutAppOpsSaver(pkg, hidden).also { changed ->
            if (changed && SDK_INT >= P && ! hidden)
                try { AppOpsHelper(mAppContext).restoreAppOps(pkg) }
                catch (e: Exception) {
                    Toasts.showLong(mAppContext, R.string.prompt_failed_preserving_app_ops)
                    analytics().logAndReport(TAG, "Error restoring app ops settings for $pkg", e) }}
    }

    fun setApplicationHiddenWithoutAppOpsSaver(pkg: String?, hidden: Boolean): Boolean =
        manager.setApplicationHidden(sAdmin, pkg, hidden).also { changed ->
            if (changed && ! hidden)
                Modules.broadcast(mAppContext, Intent(ACTION_PACKAGE_UNFROZEN, Uri.fromParts("package", pkg, null))) }

    fun setUserRestriction(restriction: String, enabled: Boolean) =
        if (enabled) manager.addUserRestriction(sAdmin, restriction)
        else manager.clearUserRestriction(sAdmin, restriction)

    constructor(context: Context) {
        mAppContext = context.applicationContext
        manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        cacheDeviceAdminComponent(context)
    }

    @RequiresApi(P) private constructor(context: Context, profile: UserHandle) {
        val profileAppInfo = LauncherAppsCompat(context).getApplicationInfoNoThrows(Modules.MODULE_ENGINE, 0, profile)
            ?: context.applicationInfo.apply { uid = UserHandles.getUid(profile.toId(), UserHandles.getAppId(Process.myUid())) }
        mAppContext = try { Hacks.Context_createApplicationContext.invoke(profileAppInfo, 0).on(context) }
        catch (e: NameNotFoundException) { throw IllegalStateException(e) } // Should never happen
        manager = mAppContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        cacheDeviceAdminComponent(context)
    }

    /* Helpers for general APIs in DevicePolicyManager */
    interface TriConsumer<A, B, C> { fun accept(a: A, b: B, c: C) }
    interface QuadConsumer<A, B, C, D> { fun accept(a: A, b: B, c: C, d: D) }

    /* Java */fun execute(callee: BiConsumer<DPM, ComponentName>) = callee.accept(manager, sAdmin)
    /* Java */fun <A> execute(callee: TriConsumer<DPM, ComponentName, A>, a: A) = callee.accept(manager, sAdmin, a)
    fun <A> execute(callee: Function3<DPM, ComponentName, A, Unit>, a: A) = callee.invoke(manager, sAdmin, a)
    /* Java */fun <A, B> execute(callee: QuadConsumer<DPM, ComponentName, A, B>, a: A, b: B) = callee.accept(manager, sAdmin, a, b)
    fun <A, B> execute(callee: Function4<DPM, ComponentName, A, B, Unit>, a: A, b: B) = callee.invoke(manager, sAdmin, a, b)
    operator fun <T> invoke(callee: Function2<DPM, ComponentName, T>): T = callee.invoke(manager, sAdmin)
    operator fun <A, T> invoke(callee: Function3<DPM, ComponentName, A, T>, a: A): T = callee.invoke(manager, sAdmin, a)
    operator fun <A, B, T> invoke(callee: Function4<DPM, ComponentName, A, B, T>, a: A, b: B): T = callee.invoke(manager, sAdmin, a, b)
    operator fun <A, B, C, T> invoke(callee: Function5<DPM, ComponentName, A, B, C, T>, a: A, b: B, c: C): T = callee.invoke(manager, sAdmin, a, b, c)

    val manager: DevicePolicyManager
    private val mAppContext: Context

    companion object {

        const val ACTION_PACKAGE_UNFROZEN = "com.oasisfeng.island.action.PACKAGE_UNFROZEN"

        @JvmStatic fun getProfileOwnerAsUser(context: Context, profile: UserHandle): Optional<ComponentName>? = when {
            SDK_INT > P -> null
            SDK_INT == P -> Hacks.DevicePolicyManager_getProfileOwner.let {
                if (it.isAbsent) null
                else try { Optional.ofNullable(it.invoke().on(DevicePolicies(context, profile).manager)) }
                catch (e: RuntimeException) { null }}
            else/* SDK_INT < P */-> getProfileOwnerAsUser(context, profile.toId())
        }

        /** @return the profile owner component (may not be present), or null for failure */
        private fun getProfileOwnerAsUser(context: Context, profile_id: Int): Optional<ComponentName>? {
            if (Hacks.DevicePolicyManager_getProfileOwnerAsUser.isAbsent) return null
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return try { Optional.ofNullable(Hacks.DevicePolicyManager_getProfileOwnerAsUser.invoke(profile_id).on(dpm)) }
            catch (e: RuntimeException) { null }
        }

        private fun cacheDeviceAdminComponent(context: Context) {
            sAdmin = DeviceAdmins.getComponentName(context)
        }

        private lateinit var sAdmin: ComponentName
    }
}

private const val TAG = "Island.DP"
