package com.oasisfeng.island.shuttle;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;
import com.oasisfeng.island.shortcut.ShortcutIcons;
import com.oasisfeng.island.util.Cryptography;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import java.security.GeneralSecurityException;
import java.security.ProviderException;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/**
 * Redirect shortcut creation request to owner user.
 *
 * TODO: Support shortcut request on Android O+, by registering Island as current launcher in managed profile.
 *
 * Created by Oasis on 2017/9/17.
 */
public class ShortcutShuttle extends BroadcastReceiver {

	private static final String EXTRA_TOKEN = "token";

	@Override public void onReceive(final Context context, final Intent intent) {
		if (intent.getAction() == null) return;
		final Intent target_intent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
		final String shortcut_name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

		if (! Users.isOwner()) {
			if (! Permissions.has(context, Permissions.INTERACT_ACROSS_USERS)) return;        // TODO: Support Android O+ & 5.x.
			final String target_intent_uri = target_intent.toUri(0);
			try {
				final String signature = Cryptography.sign(context, target_intent_uri);
				intent.putExtra(AppLaunchShortcut.EXTRA_SIGNATURE, signature);
			} catch (final GeneralSecurityException | ProviderException e) {
				Analytics.$().event("intent_sign_error").with(ITEM_ID, target_intent_uri).with(ITEM_CATEGORY, e.getClass().getCanonicalName()).send();
				Analytics.$().report(e);
			}
			// Send to myself in owner user, handled above.
			final PendingIntent token = PendingIntent.getBroadcast(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
			context.sendBroadcastAsUser(intent.putExtra(EXTRA_TOKEN, token).setComponent(new ComponentName(context, getClass())), Users.owner);
			return;
		}

		if (BuildConfig.DEBUG && ! intent.hasExtra(EXTRA_TOKEN)) {
			final Intent _intent = new Intent(target_intent); _intent.removeExtra(AppLaunchShortcut.EXTRA_SIGNATURE);
			NotificationIds.Debug.post(context, shortcut_name, new Notification.Builder(context).setSmallIcon(android.R.drawable.ic_menu_add)
					.setContentTitle("Shortcut: " + shortcut_name).setContentText(_intent.toString().substring(7))
					.setStyle(new Notification.BigTextStyle().bigText(_intent.toUri(0))));
		}

		final UserHandle profile = Users.profile;
		if (profile == null) return;

		// Verify the token to ensure that sender is indeed myself in profile, ignoring regular shortcut installation requests in owner user (including the one forwarded by us).
		final PendingIntent token = intent.getParcelableExtra(EXTRA_TOKEN);
		if (token == null) return;
		if (! profile.equals(token.getCreatorUserHandle()) || ! context.getPackageName().equals(token.getCreatorPackage())) {
			Log.w(TAG, "Invalid token: UID=" + token.getCreatorUid());
			return;
		}
		Log.i(TAG, "Forward shortcut pinning request: " + shortcut_name);

		Bitmap shortcut_icon = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
		if (shortcut_icon == null) {
			final Intent.ShortcutIconResource icon_res = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
			if (icon_res == null) {
				Log.w(TAG, "No icon provided in shortcut pinning request: " + shortcut_name);
				return;
			}
			final PackageManager pm = context.getPackageManager();
			final Resources pkg_res;
			try { @SuppressLint("WrongConstant")
				final ApplicationInfo app_info = pm.getApplicationInfo(icon_res.packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
				pkg_res = pm.getResourcesForApplication(app_info);
			} catch (final PackageManager.NameNotFoundException e) {
				Log.w(TAG, "Package of icon resource not found: " + icon_res.packageName);
				return;
			}
			final int res_id = pkg_res.getIdentifier(icon_res.resourceName, null, null);
			if (res_id == 0) {
				Log.w(TAG, "Icon resource not found for shortcut pinning request (" + shortcut_name + "): " + icon_res.resourceName);
				return;
			}
			final Drawable badged_icon = pm.getUserBadgedIcon(pkg_res.getDrawable(res_id), profile);
			shortcut_icon = ShortcutIcons.createLargeIconBitmap(context, badged_icon, icon_res.packageName);
			if (shortcut_icon == null) {
				Log.w(TAG, "Failed to build icon bitmap for icon resource: " + icon_res.resourceName);
				return;
			}
		}

		final Intent shuttle_intent = AppLaunchShortcut.createShortcutOnLauncher(target_intent);
		shuttle_intent.putExtra(AppLaunchShortcut.EXTRA_SIGNATURE, intent.getStringExtra(AppLaunchShortcut.EXTRA_SIGNATURE));
		final String id = "#" + shortcut_name.hashCode() + "@" + Users.toId(Users.profile);        // #<hash>@<user>
		final ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, id).setShortLabel(shortcut_name)
				.setIcon(IconCompat.createWithBitmap(shortcut_icon)).setIntent(shuttle_intent).build();
		ShortcutManagerCompat.requestPinShortcut(context, info, null);
	}

	private static final String TAG = ShortcutShuttle.class.getSimpleName();
}
