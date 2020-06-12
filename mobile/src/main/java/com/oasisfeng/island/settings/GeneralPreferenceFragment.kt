package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.preference.TwoStatePreference
import com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Permissions
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * General preferences in Settings
 *
 * Extracted from SettingsActivity by Oasis on 2019/7/17.
 */
@Suppress("DEPRECATION")
class GeneralPreferenceFragment : SettingsActivity.SubPreferenceFragment(R.xml.pref_general, R.string.key_launch_shortcut_prefix) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setup<TwoStatePreference>(R.string.key_show_admin_message) {
            val policies by lazy { DevicePolicies(activity) }
            if (SDK_INT < N || ! policies.isActiveDeviceOwner) return@setup remove(this)

            isChecked = policies.invoke(DevicePolicyManager::getShortSupportMessage) != null
            onChange { enabled ->
                policies.execute(DevicePolicyManager::setShortSupportMessage, if (enabled) getText(R.string.device_admin_support_message_short) else null)
                policies.execute(DevicePolicyManager::setLongSupportMessage, if (enabled) getText(R.string.device_admin_support_message_long) else null)
                true
            }
        }

        setup<TwoStatePreference>(R.string.key_preserve_app_ops) {
            if (SDK_INT < P) return@setup remove(this)
            onChange { enabled -> true.also {
                if (enabled && ! Permissions.has(activity, GET_APP_OPS_STATS)) GlobalScope.launch(Dispatchers.Default) {
                    Shell.SU.run("pm grant " + Modules.MODULE_ENGINE + " " + GET_APP_OPS_STATS) }
            }}
        }
    }
}
