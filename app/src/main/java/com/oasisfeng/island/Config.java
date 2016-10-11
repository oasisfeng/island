package com.oasisfeng.island;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * All URLs
 *
 * Created by Oasis on 2016/5/26.
 */
public enum Config {

	URL_CANNOT_CLONE_EXPLAINED("url_cannot_clone"),
	URL_PREREQUISITES("url_prerequisites");

	public String get() { return FirebaseRemoteConfig.getInstance().getString(key); }
	Config(final String key) { this.key = key; }

	private final String key;

	static {
		final FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(BuildConfig.DEBUG).build();
		final FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
		config.setConfigSettings(settings);
		config.setDefaults(R.xml.config_defaults);
	}
}
