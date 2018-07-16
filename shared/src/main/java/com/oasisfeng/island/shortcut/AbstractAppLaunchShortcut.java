package com.oasisfeng.island.shortcut;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.Nullable;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Users;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

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
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

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

	protected abstract boolean validateIncomingIntent(final Intent target, final Intent outer);
	protected abstract boolean prepareToLaunchApp(final String pkg);

	/** @return true if launcher supports shortcut pinning, false for failure, or null if legacy shortcut installation broadcast is sent, */
	public static @Nullable Boolean createOnLauncher(final Context context, final String pkg, final boolean owner, final String shortcut_prefix) {
		final PackageManager pm = context.getPackageManager();
		final List<LauncherActivityInfo> activities = Objects.requireNonNull((LauncherApps) context.getSystemService(LAUNCHER_APPS_SERVICE))
				.getActivityList(pkg, owner ? Users.owner : Users.profile);
		if (activities.isEmpty()) {
			Analytics.$().event("shortcut_non_launchable").with(ITEM_ID, pkg).send();
			return false;
		}
		final LauncherActivityInfo activity = activities.get(0);
		final ComponentName component = activity.getComponentName();
		final String label = shortcut_prefix + activity.getLabel();
		final Supplier<Bitmap> icon_bitmap = () -> ShortcutIcons.createLargeIconBitmap(context, owner || SDK_INT >= O ? activity.getIcon(0)
				: activity.getBadgedIcon(0), pkg);		// No badge icon on Android O, since launcher will use the icon of Island as badge.

		final Intent launch_intent = new Intent(owner ? ACTION_LAUNCH_APP : ACTION_LAUNCH_CLONE).addCategory(CATEGORY_LAUNCHER)
				.setData(Uri.fromParts("target", component.flattenToShortString(), null));

		if (SDK_INT >= O) {
			final ShortcutInfo.Builder info = new ShortcutInfo.Builder(context, getShortcutId(pkg, owner ? null : Users.profile)).setShortLabel(label)
					.setIntent(launch_intent).setIcon(Icon.createWithBitmap(icon_bitmap.get()));	// createWithResource may not compatible with all launchers.
			try {
				return Objects.requireNonNull((ShortcutManager) context.getSystemService(SHORTCUT_SERVICE)).requestPinShortcut(info.build(), null);
			} catch (final RuntimeException e) {
				Analytics.$().report(e);
				return false;
			}
		} else {
			final Intent intent = new Intent(ACTION_INSTALL_SHORTCUT).putExtra("duplicate", false)	// To prevent duplicate creation
					.putExtra(EXTRA_SHORTCUT_INTENT, launch_intent).putExtra(EXTRA_SHORTCUT_NAME, label);
			try {
				final ShortcutIconResource icon = new ShortcutIconResource();
				icon.packageName = pkg;
				icon.resourceName = context.getPackageManager().getResourcesForApplication(pkg).getResourceName(activity.getApplicationInfo().icon);
				intent.putExtra(EXTRA_SHORTCUT_ICON_RESOURCE, icon);
			} catch (final NameNotFoundException | Resources.NotFoundException e) {	// NameNotFoundException if app is not installed in owner user.
				final Bitmap bitmap = icon_bitmap.get();
				if (bitmap == null) return false;	// Analytics is already done in createLargeIconBitmap above.
				intent.putExtra(EXTRA_SHORTCUT_ICON, bitmap);
			}
			final ResolveInfo launcher = pm.resolveActivity(new Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), MATCH_DEFAULT_ONLY);
			if (launcher != null) intent.setPackage(launcher.activityInfo.packageName);
			context.sendBroadcast(intent);
			return null;
		}
	}

	/** Format: {package}[@{user}] */
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
			final ComponentName component = ComponentName.unflattenFromString(uri.getSchemeSpecificPart());
			if (component == null) return false;
			if (! prepareToLaunchApp(component.getPackageName())) return false;

			final Intent launch_intent = new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setComponent(component).addFlags(FLAG_ACTIVITY_NEW_TASK);
			try {
				startActivity(launch_intent);
			} catch (final ActivityNotFoundException e) {
				try {
					startActivity(launch_intent.setComponent(null).setPackage(component.getPackageName()));
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

	private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
	private static final String TAG = "AppShortcut";
}
