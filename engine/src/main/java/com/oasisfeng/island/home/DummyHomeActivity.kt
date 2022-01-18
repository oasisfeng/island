package com.oasisfeng.island.home

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.util.Log
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Users

class DummyHomeActivity : Activity() {

    override fun onResume() {
        super.onResume()

        if (Users.isParentProfile()) resetHomeApp()     // In case of unexpected interruption

        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        finish()
    }

    private fun resetHomeApp() {
        Log.w(TAG, "Waiving left over Home role...")
        packageManager.setComponentEnabledSetting(ComponentName(this, javaClass), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
        // Trigger Home app update. "clearPersistentPreferredActivity()" alone may not trigger if it's already cleared.
        DevicePolicies(this).setPersistentPreferredActivity(DevicePolicies.PreferredActivity.Home)
        DevicePolicies(this).clearPersistentPreferredActivity(DevicePolicies.PreferredActivity.Home)
    }
}

private const val TAG = "Island.DHA"