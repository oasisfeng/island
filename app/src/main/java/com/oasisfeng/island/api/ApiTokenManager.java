package com.oasisfeng.island.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.codec.binary.Hex;

import java.security.SecureRandom;

/**
 * Manage API tokens for identity verification.
 *
 * Created by Oasis on 2016/7/26.
 */
public class ApiTokenManager {

	private static final String PREFS_NAME_API_TOKENS = "api-tokens";

	public ApiTokenManager(final Context context) {
		mTokenStore = context.getSharedPreferences(PREFS_NAME_API_TOKENS, Context.MODE_PRIVATE);
	}

	public String getToken(final String pkg) {
		final String token = mTokenStore.getString(pkg, null);
		if (token != null) return token;
		final String new_token = Hex.encodeHexString(SecureRandom.getSeed(8));
		mTokenStore.edit().putString(pkg, new_token).apply();	// TODO: Potential failure in persistence, consider pre-allocation.
		return new_token;
	}

	boolean verifyToken(final String token) {
		return mTokenStore.contains(token);
	}

	private final SharedPreferences mTokenStore;	// Key: token, String value: package
}
