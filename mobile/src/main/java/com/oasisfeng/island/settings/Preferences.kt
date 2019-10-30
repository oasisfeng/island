package com.oasisfeng.island.settings

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.preference.Preference
import android.preference.PreferenceGroup
import android.preference.TwoStatePreference
import androidx.annotation.StringRes

/**
 * Created by Oasis on 2019-10-13.
 */
@Suppress("DEPRECATION") inline fun <T: Preference> android.preference.PreferenceFragment.setup(@StringRes key: Int, crossinline block: T.() -> Unit)
        = @Suppress("UNCHECKED_CAST") (findPreference(getString(key)) as? T)?.apply { block() }

@Suppress("DEPRECATION") fun android.preference.PreferenceFragment.remove(preference: Preference) {
    if (SDK_INT >= O) preference.parent?.removePreference(preference)
    else removeLeafPreference(preferenceScreen, preference)
}

fun removeLeafPreference(root: PreferenceGroup, preference: Preference): Boolean {
    if (root.removePreference(preference)) return true
    for (i in 0 until root.preferenceCount) {
        val child = root.getPreference(i)
        if (child is PreferenceGroup && removeLeafPreference(child, preference)) return true
    }
    return false
}

inline fun Preference.onChange(crossinline block: (enabled: Boolean) -> Boolean) = setOnPreferenceChangeListener { _, v -> block(v as Boolean) }
fun TwoStatePreference.lock(checked: Boolean) { isChecked = checked; isSelectable = false }
