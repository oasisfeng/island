package com.oasisfeng.android.content.pm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.*

inline fun <reified T> Context.disableComponent() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
inline fun <reified T> Context.enableComponent() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
inline fun <reified T> Context.resetComponentState() =
		packageManager.setComponentEnabledSetting(ComponentName(this, T::class.java), COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL_APP)
