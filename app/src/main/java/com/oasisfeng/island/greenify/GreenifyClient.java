package com.oasisfeng.island.greenify;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;

import com.oasisfeng.android.app.Activities;

import java.util.Objects;

import androidx.annotation.Nullable;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Context.USER_SERVICE;

/**
 * Client helper for Greenify APIs
 *
 * Created by Oasis on 2016/10/14.
 */
public class GreenifyClient {

	private static final String ACTION_GREENIFY = "com.oasisfeng.greenify.action.GREENIFY";
	private static final String EXTRA_CALLER_ID = "caller";
	private static final int MIN_GREENIFY_VERSION = 306;    // Greenify 3.0 (build 5)

	public static boolean greenify(final Context context, final String pkg, final UserHandle user) {
		final long user_sn = Objects.requireNonNull((UserManager) context.getSystemService(USER_SERVICE)).getSerialNumberForUser(user);
		if (user_sn == - 1) return false;
		final Intent intent = new Intent(ACTION_GREENIFY).setPackage(GREENIFY_PKG).setData(Uri.parse("package:" + pkg + "#usn=" + user_sn));
		if (getGreenifyVersion(context) > 39500)        // EXTRA_CALLER_ID is supported in newer version of Greenify
			intent.putExtra(EXTRA_CALLER_ID, PendingIntent.getBroadcast(context, 0, new Intent(), FLAG_UPDATE_CURRENT))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final Activity activity = Activities.findActivityFrom(context);
		try {
			if (activity != null) activity.startActivityForResult(intent, 0);
			else context.startActivity(intent.putExtra("caller", PendingIntent.getBroadcast(context, 0, new Intent(), FLAG_UPDATE_CURRENT)));
		} catch (final ActivityNotFoundException e) {
			return false;
		}
		return true;
	}

	private static int getGreenifyVersion(final Context activity) {
		try {
			return activity.getPackageManager().getPackageInfo(GREENIFY_PKG, 0).versionCode;
		} catch (final PackageManager.NameNotFoundException e) {
			return 0;
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

	public static void openInAppMarket(final Context context) {
		final Activity activity = Activities.findActivityFrom(context);
		final Context starter = activity != null ? activity : context;
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GREENIFY_PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			starter.startActivity(intent);
		} catch (final ActivityNotFoundException e) {
			try {
				starter.startActivity(intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + GREENIFY_PKG)));
			} catch (final ActivityNotFoundException ignored) {}
		}
	}

	private static final String GREENIFY_PKG = "com.oasisfeng.greenify";
}

