package com.oasisfeng.island.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import androidx.annotation.StringRes
import com.oasisfeng.island.PersistentService
import com.oasisfeng.island.shared.R
import com.oasisfeng.settings.AppSettings

class IslandSettings(private val context: Context) {

	inner class DynamicShortcutLabel: BooleanSetting(R.string.setting_dynamic_shortcut_label)
	inner class AppSettingsHelper: PersistentServiceComponentSetting(R.string.setting_app_settings_helper)
	inner class AppSettingsHelperExtended: BooleanSetting(R.string.setting_app_settings_helper_extended)

	open inner class BooleanSetting(@StringRes prefKeyStringRes: Int, singleUser: Boolean = true)
		: IslandSetting<Boolean>(prefKeyStringRes, singleUser) {

		open val enabled get() = mAppSettings.getBoolean(this)
		open fun set(value: Boolean) = mAppSettings.set(this, value)
	}

	open inner class PersistentServiceComponentSetting(@StringRes private val keyStringRes: Int)
		: BooleanSetting(keyStringRes, singleUser = true) {

		override val enabled get() = findService().enabled
		override fun set(value: Boolean) = true.also {
			val state = if (value) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED
			context.packageManager.setComponentEnabledSetting(findService().componentName(), state, DONT_KILL_APP) }

		private fun findService(): ServiceInfo {
			val services = context.packageManager.queryIntentServices(Intent(PersistentService.SERVICE_INTERFACE),
					PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.GET_META_DATA)
			val key = context.getString(keyStringRes)
			val service = services.firstOrNull { it.serviceInfo.metaData?.getString("key") == key }
					?: throw IllegalStateException("No such persistent service: $key")
			return service.serviceInfo
		}

		private fun ComponentInfo.componentName() = ComponentName(packageName, name)
	}

	open inner class IslandSetting<T>(override val prefKeyResId: Int, override val isSingleUser: Boolean) : AppSettings.AppSetting<T> {
		/** Use [android.content.ContentResolver.unregisterContentObserver] to unregister. */
		fun registerObserver(observer: ContentObserver) = mAppSettings.registerObserver(this, observer)
	}

	val singleUserRootUri; get() = mAppSettings.singleUserRootUri

	private val mAppSettings = AppSettings(context)
}

