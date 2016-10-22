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

import com.oasisfeng.island.BuildConfig;

import static android.content.Context.USER_SERVICE;

/**
 * Client helper for Greenify APIs
 *
 * Created by Oasis on 2016/10/14.
 */
public class GreenifyClient {

	private static final String GREENIFY_PKG = BuildConfig.DEBUG ? "com.oasisfeng.greenify.debug" : "com.oasisfeng.greenify";
	private static final String GREENIFY_ACTION = "com.oasisfeng.greenify.action.GREENIFY";
	private static final int MIN_GREENIFY_VERSION = BuildConfig.DEBUG ? 218 : 219;	// TODO: The minimal version of Greenify with support for Island.

	public static boolean greenify(final Activity activity, final String pkg, final UserHandle user) {
		final long user_sn = ((UserManager) activity.getSystemService(USER_SERVICE)).getSerialNumberForUser(user);
		if (user_sn == -1) return false;
		final Intent intent = new Intent(GREENIFY_ACTION).setPackage(GREENIFY_PKG).setData(Uri.parse("package:" + pkg + "#usn=" + user_sn));
		try {
			activity.startActivityForResult(intent, 0);
		} catch (final ActivityNotFoundException e) {
			return false;
		}
		return true;
	}

	public static @Nullable Boolean checkGreenifyVersion(final Context context) {
		try { @SuppressWarnings("deprecation")
			final PackageInfo greenify_info = context.getPackageManager().getPackageInfo(GREENIFY_PKG, PackageManager.GET_UNINSTALLED_PACKAGES);
			return greenify_info.versionCode >= MIN_GREENIFY_VERSION;
		} catch (final PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public static void openInAppMarket(final Activity activity) {
		activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GREENIFY_PKG)));
	}
}
