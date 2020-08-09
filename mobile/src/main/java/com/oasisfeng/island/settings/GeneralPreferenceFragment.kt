@file:Suppress("DEPRECATION")

package com.oasisfeng.island.settings

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.preference.TwoStatePreference
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Permissions
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            if (! policies.isProfileOrDeviceOwnerOnCallingUser) return@setup remove(this)

            isChecked = policies.invoke(DevicePolicyManager::getShortSupportMessage) != null
            onChange { enabled ->
                policies.execute(DevicePolicyManager::setShortSupportMessage, if (enabled) getText(R.string.device_admin_support_message_short) else null)
                policies.execute(DevicePolicyManager::setLongSupportMessage, if (enabled) getText(R.string.device_admin_support_message_long) else null)
                true
            }
        }

        setup<TwoStatePreference>(R.string.key_preserve_app_ops) {
            if (SDK_INT < P) return@setup remove(this)
            if (Permissions.has(activity, GET_APP_OPS_STATS)) lock(true)
            else onChange { enabled -> false/* never toggle instantly */.also {
                if (! enabled || refreshActivationStateForPreserveAppOps()) return@also
                CoroutineScope(Dispatchers.Main).launch {
                    val cmd = "pm grant " + Modules.MODULE_ENGINE + " " + GET_APP_OPS_STATS
                    launch(Dispatchers.IO) { Shell.SU.run(cmd) }.join()

                    if (refreshActivationStateForPreserveAppOps()) return@launch
                    Dialogs.buildAlert(activity, null, getString(R.string.prompt_adb_app_ops_command) + "\n\n" + cmd)
                            .withOkButton { refreshActivationStateForPreserveAppOps() }
                            .setNeutralButton(R.string.action_copy) { _,_ ->
                                val cm = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText(null, cmd))
                            }.show() }}}}
    }

    private fun TwoStatePreference.refreshActivationStateForPreserveAppOps()
            = Permissions.has(activity, GET_APP_OPS_STATS).also { if (it) lock(true) }
}
