package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Bundle
import android.preference.TwoStatePreference
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.util.DevicePolicies

/**
 * General preferences in Settings
 *
 * Extracted from SettingsActivity by Oasis on 2019/7/17.
 */
class GeneralPreferenceFragment : SettingsActivity.SubPreferenceFragment(R.xml.pref_general, R.string.key_launch_shortcut_prefix) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference(getString(R.string.key_show_admin_message))?.let { it as TwoStatePreference }?.also { pref ->
            val policies by lazy { DevicePolicies(activity) }
            if (SDK_INT < N || ! policies.isActiveDeviceOwner) { removeLeafPreference(preferenceScreen, pref); return }

            pref.isChecked = policies.invoke(DevicePolicyManager::getShortSupportMessage) != null
            pref.setOnPreferenceChangeListener { _, value -> val enabled = value as Boolean
                policies.execute(DevicePolicyManager::setShortSupportMessage, if (enabled) getText(R.string.device_admin_support_message_short) else null)
                policies.execute(DevicePolicyManager::setLongSupportMessage, if (enabled) getText(R.string.device_admin_support_message_long) else null)
                true
            }
        }
    }
}
