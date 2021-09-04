package com.oasisfeng.island.engine

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.util.*

/**
 * Utilities of shared basic functionality for modules
 *
 * Created by Oasis on 2017/2/20.
 */
object IslandManager {

    @JvmStatic fun ensureLegacyInstallNonMarketAppAllowed(context: Context, policies: DevicePolicies): Boolean {
        policies.clearUserRestrictionsIfNeeded(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        if (SDK_INT >= O) return true // INSTALL_NON_MARKET_APPS is no longer supported since Android O.
        val resolver = context.contentResolver
        @Suppress("DEPRECATION", "LocalVariableName") val INSTALL_NON_MARKET_APPS = Settings.Secure.INSTALL_NON_MARKET_APPS
        if (Settings.Secure.getInt(resolver, INSTALL_NON_MARKET_APPS, 0) > 0) return true
        policies.execute(DPM::setSecureSetting, INSTALL_NON_MARKET_APPS, "1")
        return Settings.Secure.getInt(resolver, INSTALL_NON_MARKET_APPS, 0) > 0
    }

    @JvmStatic @OwnerUser @ProfileUser fun ensureAppHiddenState(context: Context, pkg: String, state: Boolean): Boolean {
        val policies = DevicePolicies(context)
        if (policies.setApplicationHidden(pkg, state)) return true
        // Since setApplicationHidden() return false if already in that state, also check the current state.
        val hidden = policies(DPM::isApplicationHidden, pkg)
        return state == hidden
    }

    /** @return error information, or empty string for success. */
    @JvmStatic @OwnerUser @ProfileUser fun ensureAppFreeToLaunch(context: Context, pkg: String): String {
        val policies = DevicePolicies(context)
        if (policies(DPM::isApplicationHidden, pkg) && ! policies.setApplicationHidden(pkg, false)
            && ! Apps.of(context).isInstalledInCurrentUser(pkg))
                return "not_installed" // Not installed in profile, just give up.
        try { if (policies.isPackageSuspended(pkg)) policies(DPM::setPackagesSuspended, arrayOf(pkg), false) }
        catch (_: PackageManager.NameNotFoundException) { return "not_found" }
        return ""
    }

    @JvmStatic @OwnerUser fun launchApp(context: Context, pkg: String, profile: UserHandle): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        try {
            val activities = launcherApps.getActivityList(pkg, profile)
            if (activities.isNullOrEmpty()) return false
            launcherApps.startMainActivity(activities[0].componentName, profile, null, null)
            return true }
        catch (e: SecurityException) {    // SecurityException: Cannot retrieve activities for unrelated profile 10
            Log.e(TAG, "Error launching app: $pkg @ user $profile", e)
            return false }
    }

    @JvmStatic fun getProfileIdsIncludingDisabled(context: Context): IntArray =
        context.getSystemService<UserManager>()!!.getProfileIds(Users.currentId(), false)
            ?: context.getSystemService<UserManager>()!!.userProfiles.map(UserHandle::toId).toIntArray() // Fallback to profiles without disabled.
}

private const val TAG = "Island.Manager"
