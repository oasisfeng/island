package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.preference.Preference
import android.preference.TwoStatePreference
import androidx.annotation.StringRes
import com.oasisfeng.android.ui.Dialogs
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
class GeneralPreferenceFragment : SettingsActivity.SubPreferenceFragment(R.xml.pref_general, R.string.key_launch_shortcut_prefix) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setup<TwoStatePreference>(R.string.key_show_admin_message) {
            val policies by lazy { DevicePolicies(activity) }
            if (SDK_INT < N || ! policies.isActiveDeviceOwner) return@setup remove()

            isChecked = policies.invoke(DevicePolicyManager::getShortSupportMessage) != null
            onChange { enabled ->
                policies.execute(DevicePolicyManager::setShortSupportMessage, if (enabled) getText(R.string.device_admin_support_message_short) else null)
                policies.execute(DevicePolicyManager::setLongSupportMessage, if (enabled) getText(R.string.device_admin_support_message_long) else null)
                true
            }
        }

        setup<TwoStatePreference>(R.string.key_preserve_app_ops) {
            if (SDK_INT < P) return@setup remove()
            if (Permissions.has(activity, GET_APP_OPS_STATS)) return@setup lock(true)

            summary = getString(R.string.pref_preserve_app_ops_description) + getString(R.string.pref_preserve_app_ops_adb_footnote)
            onChange { enabled ->
                if (! enabled) return@onChange true
                if (SDK_INT < P) return@onChange false      // Should never happen as this Preference is already removed
                if (Permissions.has(activity, GET_APP_OPS_STATS)) return@onChange true

                false.also { GlobalScope.launch(Dispatchers.Default) {      // Toggle it after successful root execution
                    val cmd = "pm grant " + Modules.MODULE_ENGINE + " " + GET_APP_OPS_STATS
                    Shell.SU.run(cmd)
                    launch(Dispatchers.Main) { activity?.also { activity ->
                        if (Permissions.has(activity, GET_APP_OPS_STATS)) lock(true)
                        else Dialogs.buildAlert(activity, null, getString(R.string.prompt_adb_app_ops_command) + "\n\n" + cmd)
                                .withOkButton(null).setNeutralButton(R.string.action_copy) { _,_ ->
                                    (activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip = ClipData.newPlainText(null, cmd)
                                }.show()
                    }}
                }}
            }
        }
    }

    private inline fun <T: Preference> setup(@StringRes key: Int, crossinline block: T.() -> Unit)
            = @Suppress("UNCHECKED_CAST") (findPreference(getString(key)) as? T)?.apply { block() }
    private inline fun Preference.onChange(crossinline block: (enabled: Boolean) -> Boolean)
            = setOnPreferenceChangeListener { _, v -> block(v as Boolean) }
    private fun TwoStatePreference.lock(checked: Boolean) { isChecked = checked; isSelectable = false }
    private fun Preference.remove(): Unit
            = removeLeafPreference(preferenceScreen, this).let { return }
}
