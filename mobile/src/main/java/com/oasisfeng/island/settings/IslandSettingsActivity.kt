@file:Suppress("DEPRECATION")

package com.oasisfeng.island.settings

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_PHONE_STATE
import android.Manifest.permission.READ_SMS
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE
import android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.*
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.TwoStatePreference
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.ArraySet
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.ui.WebContent
import com.oasisfeng.island.Config
import com.oasisfeng.island.TempDebug
import com.oasisfeng.island.appops.AppOpsCompat
import com.oasisfeng.island.mobile.BuildConfig
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.notification.NotificationIds
import com.oasisfeng.island.setup.IslandSetup
import com.oasisfeng.island.shuttle.PendingIntentShuttle
import com.oasisfeng.island.util.DPM
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Users

/**
 * Settings for each managed profile, also as launcher activity in managed profile.
 *
 * Created by Oasis on 2019-10-12.
 */
class IslandSettingsFragment: android.preference.PreferenceFragment() {

    override fun onResume() {
        super.onResume()
        val activity = activity
        activity.title = preferenceManager.sharedPreferences.getString(getString(R.string.key_island_name), null)
                ?: IslandNameManager.getDefaultName(activity)

        val policies = DevicePolicies(activity)
        val isProfileOrDeviceOwner = policies.isProfileOrDeviceOwnerOnCallingUser

        if (Users.isOwner() && ! isProfileOrDeviceOwner) {
            setup<Preference>(R.string.key_device_owner_setup) {
                summary = getString(R.string.pref_device_owner_summary) + getString(R.string.pref_device_owner_featurs)
                setOnPreferenceClickListener { true.also { WebContent.view(activity, Uri.parse(Config.URL_SETUP_GOD_MODE.get())) }}}
            setup<Preference>(R.string.key_privacy) { if (SDK_INT > Q) remove(this) else isEnabled = false }   // Show but disabled, as a feature preview.
            setup<Preference>(R.string.key_watcher) { isEnabled = false }
            setup<Preference>(R.string.key_island_watcher) { remove(this) }
            setup<Preference>(R.string.key_setup) { remove(this) }
            return
        } else setup<Preference>(R.string.key_device_owner_setup) { remove(this) }

        if (SDK_INT <= Q) {     // App Ops in Android R is a mess (being reset now and then), do not support it on Android R at present.
            setupPreferenceForManagingAppOps(R.string.key_manage_read_phone_state, READ_PHONE_STATE, AppOpsCompat.OP_READ_PHONE_STATE,
                    R.string.pref_privacy_read_phone_state_title, SDK_INT <= P)
            setupPreferenceForManagingAppOps(R.string.key_manage_read_sms, READ_SMS, AppOpsCompat.OP_READ_SMS,
                    R.string.pref_privacy_read_sms_title)
            setupPreferenceForManagingAppOps(R.string.key_manage_location, ACCESS_COARSE_LOCATION, AppOpsCompat.OP_COARSE_LOCATION,
                    R.string.pref_privacy_location_title)
            if (Settings.Global.getInt(activity.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0)
                setupPreferenceForManagingAppOps(R.string.key_manage_storage, READ_EXTERNAL_STORAGE,
                        AppOpsCompat.OP_READ_EXTERNAL_STORAGE, R.string.pref_privacy_storage_title)
        } else setup<Preference>(R.string.key_privacy) { remove(this) }

        setup<Preference>(R.string.key_cross_profile) {
            if (SDK_INT <= Q || ! isProfileOrDeviceOwner) return@setup remove(this)
            onClick {
                val pkgs = activity.packageManager.getInstalledPackages(GET_PERMISSIONS or MATCH_UNINSTALLED_PACKAGES)
                        .filter { it.requestedPermissions?.contains(INTERACT_ACROSS_PROFILES) == true }
                val pm = activity.packageManager
                val entries = pkgs.map { it.applicationInfo.loadLabel(pm) }.toTypedArray()
                val allowedPackages: Set<String> = policies.invoke(DPM::getCrossProfilePackages)
                val allowed = BooleanArray(entries.size) { index -> pkgs[index].packageName in allowedPackages }
                Dialogs.buildCheckList(activity, activity.getText(R.string.prompt_manage_cross_profile_apps),
                        entries, allowed) { _, which, checked -> allowed[which] = checked }
                        .setNeutralButton(R.string.action_close) { _,_ ->
                            pkgs.mapIndexedNotNullTo(ArraySet()) { index, pkg -> if (allowed[index]) pkg.packageName else null }
                                    .toSet().also { policies.invoke(DPM::setCrossProfilePackages, it) }}
                        .setPositiveButton(R.string.prompt_manage_cross_profile_apps_footer, null)
                        .show().apply { getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false } }}

        setupNotificationChannelTwoStatePreference(R.string.key_island_watcher, SDK_INT >= P && !Users.isOwner(), NotificationIds.IslandWatcher)
        setupNotificationChannelTwoStatePreference(R.string.key_app_watcher, SDK_INT >= O, NotificationIds.IslandAppWatcher)

        setup<Preference>(R.string.key_reprovision) {
            if (Users.isOwner() && ! isProfileOrDeviceOwner) return@setup remove(this)
            setOnPreferenceClickListener { true.also { @SuppressLint("InlinedApi")
                val action = if (policies.isActiveDeviceOwner) ACTION_PROVISION_MANAGED_DEVICE else ACTION_PROVISION_MANAGED_PROFILE
                ContextCompat.startForegroundService(activity, Intent(action).setPackage(Modules.MODULE_ENGINE)) }}}
        setup<Preference>(R.string.key_destroy) {
            if (Users.isOwner()) {
                if (! isProfileOrDeviceOwner) return@setup remove(this)
                setTitle(R.string.pref_rescind_title)
                summary = getString(R.string.pref_rescind_summary) + getString(R.string.pref_device_owner_featurs) + "\n" }
            setOnPreferenceClickListener { true.also {
                if (Users.isOwner()) IslandSetup.requestDeviceOrProfileOwnerDeactivation(activity)
                else IslandSetup.requestProfileRemoval(activity) }}}
    }

    private fun setupPreferenceForManagingAppOps(key: Int, permission: String, op: Int, @StringRes prompt: Int, precondition: Boolean = true) {
        setup<Preference>(key) {
            if (SDK_INT >= P && precondition) {
                setOnPreferenceClickListener { true.also { OpsManager(activity, permission, op).startOpsManager(prompt) }}
            } else remove(this) }
    }

    private fun setupNotificationChannelTwoStatePreference(@StringRes key: Int, visible: Boolean, notificationId: NotificationIds) {
        setup<TwoStatePreference>(key) {
            if (visible && SDK_INT >= O) {
                isChecked = ! notificationId.isBlocked(context)
                setOnPreferenceChangeListener { _, _ -> true.also { context.startActivity(notificationId.buildChannelSettingsIntent(context)) }}
            } else remove(this)
        }
    }

    private fun onIslandRenamed(name: String) {
        val activity = activity
        IslandNameManager.setName(activity, name)
        activity.title = name
        IslandNameManager.syncNameToOwnerUser(activity, name)
    }

    private fun requestRenaming() {
        val activity = activity
        object: EditTextPreference(activity) {
            init {
                key = getString(R.string.key_island_name)
                onAttachedToHierarchy(this@IslandSettingsFragment.preferenceManager)
                if (text.isNullOrEmpty()) text = IslandNameManager.getDefaultName(activity)
                editText.also { editText ->
                    editText.addTextChangedListener(object: TextWatcher {
                        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

                        override fun afterTextChanged(s: Editable) {
                            if (editText.text.any { it < ' ' }) editText.error = getString(R.string.prompt_invalid_input) }})

                setOnPreferenceChangeListener { _, name -> (editText.error == null).also { if (it)
                    onIslandRenamed(name.toString()) }}

                showDialog(null) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        activity.actionBar?.setDisplayHomeAsUpEnabled(true)
        preferenceManager.setStorageDeviceProtected()
        addPreferencesFromResource(R.xml.pref_island)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (! Users.isOwner()) {
            inflater.inflate(R.menu.pref_island_actions, menu)
            if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).isVisible = true }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_rename -> true.also { requestRenaming() }
            R.id.menu_test -> true.also { TempDebug.run(activity) }
            android.R.id.home -> true.also { activity.finish() }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class IslandSettingsActivity: Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != null && PendingIntentShuttle.collect(this)) return finish()    // Allow explicit launch intent without action
        setTheme(R.style.AppTheme_Settings)
        fragmentManager.beginTransaction().replace(android.R.id.content, IslandSettingsFragment()).commit()
    }

    override fun onResume() {
        super.onResume()
        setVisible(true)    // Required due to "Theme.NoDisplay" being used for PendingIntentShuttle.
    }

    override fun finish() {
        super.finish()
        if (intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0) overridePendingTransition(0, 0)
    }

    class Enabler: BroadcastReceiver() {    // One-time enabler for

        override fun onReceive(context: Context, intent: Intent) {      // ACTION_LOCKED_BOOT_COMPLETED is unnecessary for activity
            if (Intent.ACTION_BOOT_COMPLETED == intent.action || Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) context.packageManager.apply {
                if (Users.isOwner()) return         // Not needed in mainland
                setComponentEnabledSetting(ComponentName(context, IslandSettingsActivity::class.java), COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
                setComponentEnabledSetting(ComponentName(context, Enabler::class.java), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
            }
        }
    }
}

private const val INTERACT_ACROSS_PROFILES = "android.permission.INTERACT_ACROSS_PROFILES"
