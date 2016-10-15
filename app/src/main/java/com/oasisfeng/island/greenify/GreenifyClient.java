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
import com.oasisfeng.island.api.ApiActivity;
import com.oasisfeng.island.api.ApiTokenManager;

import static android.content.Context.USER_SERVICE;

/**
 * Client helper for Greenify APIs
 *
 * Created by Oasis on 2016/10/14.
 */
public class GreenifyClient {

	private static final String GREENIFY_PKG = BuildConfig.DEBUG ? "com.oasisfeng.greenify.debug" : "com.oasisfeng.greenify";
	private static final int MIN_GREENIFY_VERSION = BuildConfig.DEBUG ? 208 : 218;	// TODO: The minimal version of Greenify with support for Island.

	public static boolean greenify(final Activity activity, final String pkg, final UserHandle user) {
		final long user_sn = ((UserManager) activity.getSystemService(USER_SERVICE)).getSerialNumberForUser(user);
		if (user_sn == -1) return false;
		try {
			activity.startActivityForResult(new Intent("com.oasisfeng.greenify.action.GREENIFY").setPackage(GREENIFY_PKG)
					.setData(Uri.parse("package:" + pkg + "#usn=" + user_sn))
					.putExtra(ApiActivity.EXTRA_API_TOKEN, new ApiTokenManager(activity).getToken(GREENIFY_PKG)), 0);	// FIXME: Token is not verified in owner user.
			return true;
		} catch (final ActivityNotFoundException e) {
			return false;
		}
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
