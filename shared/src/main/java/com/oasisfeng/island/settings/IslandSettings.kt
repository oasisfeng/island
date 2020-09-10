package com.oasisfeng.island.settings

import android.content.Context
import android.database.ContentObserver
import androidx.annotation.StringRes
import com.oasisfeng.island.shared.R
import com.oasisfeng.settings.AppSettings

class IslandSettings(context: Context) {

	inner class DynamicShortcutLabel: BooleanSetting(R.string.setting_dynamic_shortcut_label)

	open inner class BooleanSetting(@StringRes prefKeyStringRes: Int, singleUser: Boolean = true)
		: IslandSetting<Boolean>(prefKeyStringRes, singleUser) {
		val enabled get() = mAppSettings.getBoolean(this)
		fun set(value: Boolean) = mAppSettings.set(this, value)
	}

	open inner class IslandSetting<T>(override val prefKeyResId: Int, override val isSingleUser: Boolean) : AppSettings.AppSetting<T> {
		/** Use [android.content.ContentResolver.unregisterContentObserver] to unregister. */
		fun registerObserver(observer: ContentObserver) = mAppSettings.registerObserver(this, observer)
	}

	val singleUserRootUri; get() = mAppSettings.singleUserRootUri

	private val mAppSettings = AppSettings(context)
}

