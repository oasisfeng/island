package com.oasisfeng.island.home

import android.content.Context
import android.content.pm.PackageManager.*
import android.content.pm.ResolveInfo
import android.util.Log
import com.oasisfeng.hack.Hack
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Hacks
import kotlinx.coroutines.delay

object HomeRole {

    suspend fun runWithHomeRole(context: Context, block: suspend () -> Unit): Boolean {
        val pm = context.packageManager; val policies = DevicePolicies(context)
        val homeRole = DevicePolicies.PreferredActivity.Home
        val dummyHome = policies.findUniqueMatchingActivity(homeRole)
        val currentHome = getDefaultHome(context)

        pm.setComponentEnabledSetting(dummyHome, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
        for (index in 0 until 10) {
            Log.i(TAG, "Acquiring home role...")
            // In case they are not reset by accident, clear and then add, to ensure default Home update in PMS is always triggered.
            policies.clearPersistentPreferredActivity(homeRole)
            policies.clearPersistentPreferredActivity(currentHome.packageName)
            policies.setPersistentPreferredActivity(homeRole)

            if (getDefaultHome(context) == dummyHome) {
                Log.i(TAG, "Acquired home role")
                try { block() }
                finally {
                    Log.i(TAG, "Waiving home role...")
                    pm.setComponentEnabledSetting(dummyHome, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP) }
                    policies.clearPersistentPreferredActivity(homeRole)
                    if (currentHome != null) {  // Restore home app to the previous one (needed if more than 1 home app installed)
                        Log.i(TAG, "Restoring home role...")
                        policies.setPersistentPreferredActivity(homeRole, currentHome)
                        policies.clearPersistentPreferredActivity(currentHome.packageName) }
                    return true }

            delay(500)      // It may not work for the first few times, just try again in a short delay.
        }
        Log.w(TAG, "Failed to acquired default home role")
        return false
    }

    private fun getDefaultHome(context: Context) =
        Hack.into(context.packageManager).with(Hacks.PackageManagerHack::class.java).getHomeActivities(ArrayList<ResolveInfo>())
}

private const val TAG = "Island.Home"