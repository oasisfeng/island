@file:Suppress("DEPRECATION")

package com.oasisfeng.island.settings

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.preference.*
import androidx.annotation.StringRes

inline fun <T: Preference> PreferenceFragment.setup(@StringRes key: Int, crossinline block: T.() -> Unit)
        = @Suppress("UNCHECKED_CAST") (findPreference(getString(key)) as? T)?.apply { block() }

@Suppress("DEPRECATION") fun PreferenceFragment.remove(preference: Preference) {
    if (SDK_INT >= O) preference.parent?.apply { removePreference(preference); if (this is PreferenceCategory && preferenceCount == 0) remove(this) }
    else removeLeafPreference(preferenceScreen, preference)
}

fun removeLeafPreference(root: PreferenceGroup, preference: Preference): Boolean {
    if (root.removePreference(preference)) return true
    for (i in 0 until root.preferenceCount) {
        val child = root.getPreference(i)
        if (child is PreferenceGroup && removeLeafPreference(child, preference)) {
            if (child is PreferenceCategory && child.preferenceCount == 0) root.removePreference(child)
            return true
        }
    }
    return false
}

inline fun TwoStatePreference.onChange(crossinline block: (enabled: Boolean) -> Boolean) = setOnPreferenceChangeListener { _, v -> block(v as Boolean) }
inline fun EditTextPreference.onChange(crossinline block: (value: String) -> Boolean) = setOnPreferenceChangeListener { _, v -> block(v as String) }
fun TwoStatePreference.lock(checked: Boolean) { isChecked = checked; isSelectable = false }
