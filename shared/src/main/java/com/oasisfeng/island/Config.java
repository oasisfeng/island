package com.oasisfeng.island;

import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.shared.R;
import com.oasisfeng.pattern.GlobalContextProvider;

/**
 * Remotely configurable values, (default values are defined in config_defaults.xml)
 *
 * Created by Oasis on 2016/5/26.
 */
public enum Config {

	/* All keys must be consistent with config_defaults.xml */
	IS_REMOTE("is_remote"),
	URL_FAQ("url_faq"),
	URL_SETUP("url_setup");

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
		FirebaseApp.initializeApp(GlobalContextProvider.get());
		final FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(BuildConfig.DEBUG).build();
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.setConfigSettings(settings);
		config.setDefaults(R.xml.config_defaults);
		config.fetch();
	}
}
