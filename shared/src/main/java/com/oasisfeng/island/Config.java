package com.oasisfeng.island;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.oasisfeng.island.firebase.FirebaseWrapper;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.shared.R;

/**
 * Remotely configurable values, (default values are defined in config_defaults.xml)
 *
 * Created by Oasis on 2016/5/26.
 */
public enum Config {

	/* All keys must be consistent with config_defaults.xml */
	IS_REMOTE("is_remote"),
	URL_FAQ("url_faq"),
	URL_SETUP("url_setup"),
	URL_SETUP_MANAGED_MAINLAND("url_setup_god_mode"),
	URL_SETUP_TROUBLESHOOTING("url_setup_trouble"),
	PERMISSION_REQUEST_ALLOWED_APPS("permission_allowed_apps");

	public String get() {
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.activateFetched();
		return config.getString(key);
	}

	public static boolean isRemote() {
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.activateFetched();
		final FirebaseRemoteConfigValue value = config.getValue(IS_REMOTE.key);
		return value.getSource() == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE;
	}

	Config(final String key) { this.key = key; }

	private final String key;

	static {
		FirebaseWrapper.init();
		final FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(BuildConfig.DEBUG).build();
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.setConfigSettings(settings);
		config.setDefaults(R.xml.config_defaults);
		config.fetch();
	}
}
