package com.oasisfeng.island;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.RestrictionsManager;
import android.os.PersistableBundle;
import android.os.UserHandle;

/**
 * API-related constants
 *
 * Created by Oasis on 2019-6-10.
 */
@SuppressLint("InlinedApi") public class ApiConstants {

	/**
	 * Both as request type in {@link RestrictionsManager#requestPermission(String, String, PersistableBundle)}
	 * and restriction key in {@link RestrictionsManager#getApplicationRestrictions()} (value: authorized delegations in string array)
	 */
	static final String TYPE_DELEGATION = "com.oasisfeng.island.delegation";	// Name-spaced as requirement mentioned by RestrictionsManager.requestPermission()

	/**
	 * Optional user serial number to request cross-user authorization. (Without this key, default to current user only)
	 *
	 * @see android.os.UserManager#getSerialNumberForUser(UserHandle)
	 */
	public static final String REQUEST_KEY_USER_SERIAL_NUMBER = "com.oasisfeng.island.request.user";

	/** Special value for {@link #REQUEST_KEY_USER_SERIAL_NUMBER}, to apply for all users. */
	public static final long USER_ALL = -1;

	public static final String DELEGATION_PACKAGE_ACCESS = DevicePolicyManager.DELEGATION_PACKAGE_ACCESS;
	/* Custom delegations with prefix "-island-" */
	public static final String DELEGATION_APP_OPS = "-island-delegation-app-ops";
}
