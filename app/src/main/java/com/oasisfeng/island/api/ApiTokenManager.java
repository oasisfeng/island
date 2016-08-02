package com.oasisfeng.island.api;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

/**
 * Manage API tokens for identity verification.
 *
 * Created by Oasis on 2016/7/26.
 */
public class ApiTokenManager {

	private static final String PREFS_NAME_API_TOKENS = "api-tokens";
	private static final char[] DIGITS_UPPER = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	public ApiTokenManager(final Context context) {
		mTokenStore = context.getSharedPreferences(PREFS_NAME_API_TOKENS, Context.MODE_PRIVATE);
	}

	public String getToken(final String pkg) {
		final String token = mTokenStore.getString(pkg, null);
		if (token != null) return token;
		final String new_token = generateToken();
		mTokenStore.edit().putString(pkg, new_token).apply();	// TODO: Potential failure in persistence, consider pre-allocation.
		return new_token;
	}

	private String generateToken() {
		final byte[] data = SecureRandom.getSeed(8);
		final char[] token = new char[data.length << 1];
		for (int i = 0, j = 0; i < data.length; i ++) {
			token[j ++] = DIGITS_UPPER[(240 & data[i]) >>> 4];
			token[j ++] = DIGITS_UPPER[15 & data[i]];
		}
		return new String(token);
	}

	boolean verifyToken(final String token) {
		return mTokenStore.getAll().containsValue(token);	// TODO: Optimize the performance
	}

	private final SharedPreferences mTokenStore;	// Key: token, String value: package
}
