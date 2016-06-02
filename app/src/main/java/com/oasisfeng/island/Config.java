package com.oasisfeng.island;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * All URLs
 *
 * Created by Oasis on 2016/5/26.
 */
public class Config {

	private static final FirebaseRemoteConfig sRemoteConfig = FirebaseRemoteConfig.getInstance();
	static {
		final FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder()
				.setDeveloperModeEnabled(BuildConfig.DEBUG).build();
		sRemoteConfig.setConfigSettings(settings);
		sRemoteConfig.setDefaults(R.xml.config_defaults);
	}

	public static final String URL_CANNOT_CLONE_EXPLAINED = sRemoteConfig.getString("url_cannot_clone");
	public static final String URL_ABOUT_ENCRYPTION = sRemoteConfig.getString("url_about_encryption");
}
