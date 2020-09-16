package com.oasisfeng.island.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import javax.annotation.ParametersAreNonnullByDefault;

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

	public static boolean has(final Context context, final String permission) {
		return context.checkPermission(permission, PID, UID) == PackageManager.PERMISSION_GRANTED;
	}
}
