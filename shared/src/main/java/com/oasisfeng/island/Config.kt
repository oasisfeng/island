package com.oasisfeng.island

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler
import com.oasisfeng.island.firebase.FirebaseWrapper
import com.oasisfeng.island.shared.BuildConfig

/**
 * Remotely configurable values
 *
 * Created by Oasis on 2016/5/26.
 */
enum class Config(private val key: String, private val default: String) {
	IS_REMOTE("is_remote", ""),
	URL_PLAY_ALPHA("url_play_alpha", "https://groups.google.com/g/islandroid"),
	URL_FAQ("url_faq", "https://island.oasisfeng.com/faq"),
	URL_SETUP("url_setup", "https://island.oasisfeng.com/setup"),
	URL_SETUP_MANAGED_MAINLAND("url_setup_god_mode", "https://island.oasisfeng.com/setup#activate-managed-mainland"),
	URL_SETUP_TROUBLESHOOTING("url_setup_trouble", "https://island.oasisfeng.com/faq"),
	PERMISSION_REQUEST_ALLOWED_APPS("permission_allowed_apps", "com.oasisfeng.greenify,com.oasisfeng.nevo");

	fun get(): String = FirebaseRemoteConfig.getInstance().getString(key).ifEmpty { default }

	companion object {

		@JvmStatic fun isRemote(): Boolean {
			val value = FirebaseRemoteConfig.getInstance().getValue(IS_REMOTE.key)
			return value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
		}

		init {
			FirebaseWrapper.init()
			val settings = FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(
				if (BuildConfig.DEBUG) 30 else ConfigFetchHandler.DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS)
			FirebaseRemoteConfig.getInstance().apply {
				setConfigSettingsAsync(settings.build()).addOnCompleteListener { fetchAndActivate() }}
		}
	}
}