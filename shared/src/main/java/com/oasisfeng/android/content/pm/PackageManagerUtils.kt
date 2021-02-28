package com.oasisfeng.android.content.pm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import androidx.core.content.ContextCompat

inline fun <reified T : Any> Context.getSystemService(): T? =
		ContextCompat.getSystemService(this, T::class.java)

inline fun <reified T> Context.disableComponent() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
inline fun <reified T> Context.enableComponent() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
inline fun <reified T> Context.resetComponentState() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL_APP)
