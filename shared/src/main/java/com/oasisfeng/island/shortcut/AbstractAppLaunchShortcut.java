package com.oasisfeng.island.shortcut;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.DisplayMetrics;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Users;

import java.net.URISyntaxException;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.Intent.EXTRA_SHORTCUT_ICON;
import static android.content.Intent.EXTRA_SHORTCUT_ICON_RESOURCE;
import static android.content.Intent.EXTRA_SHORTCUT_INTENT;
import static android.content.Intent.EXTRA_SHORTCUT_NAME;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.ShortcutIconResource;
import static android.content.Intent.URI_INTENT_SCHEME;
import static android.content.Intent.parseUri;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;

/**
 * Create launch shortcut for apps in Island or mainland, from the owner user space.
 *
 * TODO: Secure both the installer and launch intent to protect again abuse.
 *
 * Created by Oasis on 2016/4/22.
 */
@ParametersAreNonnullByDefault
public abstract class AbstractAppLaunchShortcut extends Activity {

	public static final String ACTION_LAUNCH_CLONE = "com.oasisfeng.island.action.LAUNCH_CLONE";
	private static final String ACTION_LAUNCH_APP = "com.oasisfeng.island.action.LAUNCH_APP";
	private static final String SCHEME_PACKAGE = "package";		// Introduced in Island 2.8
	private static final String SCHEME_TARGET = "target";		// DO NOT REMOVE, used by shortcut created with Island 2.7.5 and before.
	private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

	protected abstract boolean validateIncomingIntent(final Intent target, final Intent outer);
	protected abstract boolean prepareToLaunchApp(final String pkg);

	/** @return true if launcher supports shortcut pinning, false for failure, or null if legacy shortcut installation broadcast is sent, */
	public static @Nullable Boolean createOnLauncher(final Context context, final String pkg, final ApplicationInfo app, final UserHandle user,
													 final CharSequence label, final @DrawableRes int icon) {
		final boolean for_owner = Users.isOwner(user);
		final String action = for_owner ? ACTION_LAUNCH_APP : ACTION_LAUNCH_CLONE;
		final Intent launch_intent = new Intent(action, Uri.fromParts(SCHEME_PACKAGE, pkg, null)).addCategory(CATEGORY_LAUNCHER);

		if (SDK_INT >= O) {
			final ShortcutManager sm = context.getSystemService(ShortcutManager.class);
			if (sm == null) return null;
			final ShortcutInfo.Builder info = new ShortcutInfo.Builder(context, getShortcutId(pkg, for_owner ? null : Users.profile)).setShortLabel(label)
					.setIntent(launch_intent).setIcon(Icon.createWithBitmap(makeShortcutIconBitmap(context, app, user, icon)));	// createWithResource() cannot handle profile app, and may not compatible with all launchers.
			try {
				return sm.requestPinShortcut(info.build(), null);
			} catch (final RuntimeException e) {
				Analytics.$().report(e);
				return false;
			}
		} else {
			final Intent intent = new Intent(ACTION_INSTALL_SHORTCUT).putExtra("duplicate", false)	// To prevent duplicate creation
					.putExtra(EXTRA_SHORTCUT_INTENT, launch_intent).putExtra(EXTRA_SHORTCUT_NAME, label);
			try {
				final ShortcutIconResource shortcut_icon = new ShortcutIconResource();
				shortcut_icon.packageName = pkg;
				shortcut_icon.resourceName = context.getPackageManager().getResourcesForApplication(pkg).getResourceName(app.icon);
				intent.putExtra(EXTRA_SHORTCUT_ICON_RESOURCE, shortcut_icon);
			} catch (final NameNotFoundException | Resources.NotFoundException e) {	// NameNotFoundException if app is not installed in owner user.
				final Bitmap bitmap = makeShortcutIconBitmap(context, app, user, icon);
				if (bitmap == null) return false;	// Analytics is already done in createLargeIconBitmap above.
				intent.putExtra(EXTRA_SHORTCUT_ICON, bitmap);
			}
			final ActivityInfo launcher = new Intent(ACTION_MAIN).addCategory(CATEGORY_HOME).resolveActivityInfo(context.getPackageManager(), 0);
			if (launcher != null) intent.setPackage(launcher.packageName);
			context.sendBroadcast(intent);
			return null;
		}
	}

	private static Bitmap makeShortcutIconBitmap(final Context context, final ApplicationInfo app, final UserHandle user, final int icon) {
		final PackageManager pm = context.getPackageManager();
		Drawable drawable = null;
		if (icon != 0) try {
			final Resources resources = pm.getResourcesForApplication(app);
			drawable = resources.getDrawableForDensity(icon, getDpiForLargeIcon(resources.getDisplayMetrics().densityDpi), null);
		} catch (final NameNotFoundException | Resources.NotFoundException ignored) {}
		if (drawable == null) drawable = app.loadIcon(pm);		// Fallback to default density icon
		if (SDK_INT < O && ! Users.isOwner(user))	// Without badge icon on Android O+, since launcher will use the icon of Island as badge.
			drawable = pm.getUserBadgedIcon(drawable, user);
		return ShortcutIcons.createLargeIconBitmap(context, drawable, app.packageName);
	}

	private static int getDpiForLargeIcon(final int dpi) {
		if (dpi >= DisplayMetrics.DENSITY_XHIGH) return DisplayMetrics.DENSITY_XXHIGH;
		if (dpi >= DisplayMetrics.DENSITY_HIGH) return DisplayMetrics.DENSITY_XHIGH;
		if (dpi >= DisplayMetrics.DENSITY_MEDIUM) return DisplayMetrics.DENSITY_HIGH;
		return DisplayMetrics.DENSITY_MEDIUM;
	}

	/** Format: launch:{package}[@{user}] */
	private static String getShortcutId(final String pkg, final @Nullable UserHandle user) {
		return "launch:" + (user == null || Users.isOwner(user) ? pkg : pkg + "@" + Users.toId(user));
	}

	/** Runs in managed profile or device owner */
	private boolean launchApp(final Intent intent) {
		final Uri uri = intent.getData();
		if (uri == null) return false;

		if (uri.getEncodedFragment() != null) {		// Shortcut intent
			final Intent target_intent;
			final String intent_uri = uri.buildUpon().scheme("intent").build().toString();
			try {
				target_intent = parseUri(intent_uri, URI_INTENT_SCHEME);
			} catch (final URISyntaxException e) {
				Analytics.$().event("invalid_shortcut_uri").with(Analytics.Param.LOCATION, intent_uri).send();
				return false;
			}
			if (! validateIncomingIntent(target_intent, intent)) return false;
			final String pkg = target_intent.getComponent() != null ? target_intent.getComponent().getPackageName() : target_intent.getPackage();
			if (pkg != null) prepareToLaunchApp(pkg);
			try {
				startActivity(target_intent);
			} catch (final ActivityNotFoundException e) {
				return false;
			}
			return true;
		} else {
			final String scheme = uri.getScheme(), target = uri.getSchemeSpecificPart(), pkg;
			ComponentName component = null;
			if (SCHEME_PACKAGE.equals(scheme)) pkg = target;
			else if (SCHEME_TARGET.equals(scheme)) {
				if ((component = ComponentName.unflattenFromString(target)) == null) return false;
				pkg = component.getPackageName();
			} else return false;
			if (! prepareToLaunchApp(pkg)) return false;

			final Intent launch_intent = new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setPackage(pkg).addFlags(FLAG_ACTIVITY_NEW_TASK);
			if (component == null) {    // Entrance activity may not contain CATEGORY_DEFAULT, component must be set in launch intent.
				final ResolveInfo resolve = getPackageManager().resolveActivity(launch_intent, 0);
				if (resolve == null) return false;
				launch_intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name));
			} else launch_intent.setComponent(component);
			try {
				startActivity(launch_intent);
			} catch (final ActivityNotFoundException e) {
				if (component == null) return false;	// Already attempted to launch by package above.
				try {
					startActivity(launch_intent.setComponent(null).setPackage(pkg));
				} catch (final ActivityNotFoundException ex) {
					return false;
				}
			}
			return true;
		}
	}

	@Override protected void onCreate(final @Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		onNewIntent(getIntent());
	}

	@Override protected void onNewIntent(final Intent intent) {
		try {
			handleIntent(intent);
		} finally {
			finish();
		}
	}

	private void handleIntent(final Intent intent) {
		final String action = intent.getAction();
		if (! ACTION_LAUNCH_APP.equals(action) && ! ACTION_LAUNCH_CLONE.equals(action)) return;
		if (! launchApp(intent))
			onLaunchFailed();
	}

	protected abstract void onLaunchFailed();

	private static final String TAG = "AppShortcut";
}
