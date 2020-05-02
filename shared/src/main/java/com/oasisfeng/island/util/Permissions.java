package com.oasisfeng.island.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.annotation.StringDef;

import javax.annotation.ParametersAreNonnullByDefault;

import static android.Manifest.permission.PACKAGE_USAGE_STATS;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.os.Build.VERSION_CODES.M;

/**
 * Permission-related helpers
 *
 * Created by Oasis on 2017/10/8.
 */
@ParametersAreNonnullByDefault
public class Permissions {

	public static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";

	private static final int PID = Process.myPid();
	private static final int UID = Process.myUid();

	@TargetApi(M) @StringDef({ INTERACT_ACROSS_USERS, WRITE_SECURE_SETTINGS, PACKAGE_USAGE_STATS }) @interface DevPermission {}

	public static boolean has(final Context context, final String permission) {
		return context.checkPermission(permission, PID, UID) == PackageManager.PERMISSION_GRANTED;
	}
}
