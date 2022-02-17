package com.oasisfeng.island;

import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.oasisfeng.island.firebase.FirebaseWrapper;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.shared.R;

/**
 * Remotely configurable values
 *
 * Created by Oasis on 2016/5/26.
 */
public enum Config {

	IS_REMOTE("is_remote", ""),
	URL_PLAY_ALPHA("url_play_alpha", "https://groups.google.com/g/islandroid"),
	URL_FAQ("url_faq", "https://island.oasisfeng.com/faq"),
	URL_SETUP("url_setup", "https://island.oasisfeng.com/setup"),
	URL_SETUP_MANAGED_MAINLAND("url_setup_god_mode", "https://island.oasisfeng.com/setup#activate-managed-mainland"),
	URL_SETUP_TROUBLESHOOTING("url_setup_trouble", "https://island.oasisfeng.com/faq"),
	PERMISSION_REQUEST_ALLOWED_APPS("permission_allowed_apps", "com.oasisfeng.greenify,com.oasisfeng.nevo");

	public String get() {
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.activate();
		final String value = config.getString(key);
		return ! value.isEmpty() ? value : defaultValue;
	}

	public static boolean isRemote() {
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.activate();
		final FirebaseRemoteConfigValue value = config.getValue(IS_REMOTE.key);
		return value.getSource() == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE;
	}

	Config(final String key, final String defaultValue) { this.key = key; this.defaultValue = defaultValue; }

	private final String key;
	private final String defaultValue;

	static { //noinspection ResultOfMethodCallIgnored
		FirebaseWrapper.init();
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		final FirebaseRemoteConfigSettings.Builder settings = new FirebaseRemoteConfigSettings.Builder()
				.setMinimumFetchIntervalInSeconds(BuildConfig.DEBUG ? 30 : DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
		config.setConfigSettingsAsync(settings.build()).addOnCompleteListener(task -> config.fetch());
	}
}
