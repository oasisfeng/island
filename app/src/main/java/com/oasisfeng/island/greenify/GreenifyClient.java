package com.oasisfeng.island.greenify;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.Nullable;

import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.mobile.BuildConfig;

import static android.content.Context.USER_SERVICE;

/**
 * Client helper for Greenify APIs
 *
 * Created by Oasis on 2016/10/14.
 */
public class GreenifyClient {

	private static final String GREENIFY_ACTION = "com.oasisfeng.greenify.action.GREENIFY";
	private static final int MIN_GREENIFY_VERSION = 306;	// Greenify 3.0 (build 5)

	public static boolean greenify(final Activity activity, final String pkg, final UserHandle user) {
		final long user_sn = ((UserManager) activity.getSystemService(USER_SERVICE)).getSerialNumberForUser(user);
		if (user_sn == -1) return false;
		final Intent intent = new Intent(GREENIFY_ACTION).setPackage(getGreenifyPackage(activity)).setData(Uri.parse("package:" + pkg + "#usn=" + user_sn));
		try {
			activity.startActivityForResult(intent, 0);
		} catch (final ActivityNotFoundException e) {
			return false;
		}
		return true;
	}

	public static @Nullable Boolean checkGreenifyVersion(final Context context) {
		try { @SuppressWarnings("deprecation")
			final PackageInfo greenify_info = context.getPackageManager().getPackageInfo(getGreenifyPackage(context), PackageManager.GET_UNINSTALLED_PACKAGES);
			return greenify_info.versionCode >= MIN_GREENIFY_VERSION;
		} catch (final PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public static void openInAppMarket(final Activity activity) {
		try {
			activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GREENIFY_PKG)));
		} catch (final ActivityNotFoundException e) {
			try {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + GREENIFY_PKG)));
			} catch (final ActivityNotFoundException ignored) {}
		}
	}

	public static String getGreenifyPackage(final @Nullable Context context) {
		if (context == null) return sGreenifyPackageName != null ? sGreenifyPackageName : GREENIFY_PKG;
		if (sGreenifyPackageName == null) {
			if (BuildConfig.DEBUG && Apps.of(context).isInstalledInCurrentUser(GREENIFY_DEBUG_PKG)) sGreenifyPackageName = GREENIFY_DEBUG_PKG;
			else sGreenifyPackageName = GREENIFY_PKG;
		}
		return sGreenifyPackageName;
	}

	private static String sGreenifyPackageName;
	private static final String GREENIFY_PKG = "com.oasisfeng.greenify";
	private static final String GREENIFY_DEBUG_PKG = "com.oasisfeng.greenify.debug";
}
