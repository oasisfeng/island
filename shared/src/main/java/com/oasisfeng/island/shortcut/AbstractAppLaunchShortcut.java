package com.oasisfeng.island.shortcut;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;

/**
 * Launch shortcut for apps on Island, from the owner user space
 *
 * TODO: Secure both the installer and launch intent to protect again abuse.
 *
 * Created by Oasis on 2016/4/22.
 */
public abstract class AbstractAppLaunchShortcut extends Activity {

	public static final String ACTION_LAUNCH_CLONE = "com.oasisfeng.island.action.LAUNCH_CLONE";
	private static final String ACTION_LAUNCH_APP = "com.oasisfeng.island.action.LAUNCH_APP";

	protected abstract boolean prepareToLaunchApp(final ComponentName component);

	public static boolean createOnLauncher(final Context context, final String pkg, final boolean owner) {
		try {
			final Intent intent = buildShortcutIntent(context, pkg, owner);
			if (intent == null) return false;
			context.sendBroadcast(intent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT"));
			context.sendBroadcast(intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT"));
			return true;
		} catch (final NameNotFoundException e) {
			Log.e(TAG, "Package not installed: " + pkg);
			return false;
		}
	}

	/** @return null if the given package has no launch entrance */
	private static @Nullable Intent buildShortcutIntent(final Context context, final String pkg, final boolean owner) throws NameNotFoundException {
		final PackageManager pm = context.getPackageManager();
		@SuppressWarnings("deprecation") final List<ResolveInfo> activities = pm.queryIntentActivities(
				new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), PackageManager.GET_UNINSTALLED_PACKAGES);
		if (activities.isEmpty()) return null;

		final ActivityInfo activity = activities.get(0).activityInfo;
		final ComponentName component = new ComponentName(activity.packageName, activity.name);

		final Intent launch_intent = new Intent(owner ? ACTION_LAUNCH_APP : ACTION_LAUNCH_CLONE).addCategory(Intent.CATEGORY_LAUNCHER)
				.setData(Uri.fromParts("target", component.flattenToShortString(), null));

		final Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch_intent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "\uD83C\uDF00" + activity.loadLabel(pm));	// TODO: Unicode lock char: \uD83D\uDD12
		@SuppressWarnings("deprecation") final Drawable d = pm.getActivityInfo(component, PackageManager.GET_UNINSTALLED_PACKAGES).loadIcon(pm);
		final Bitmap icon_bitmap = drawableToBitmap(d);
		if (icon_bitmap != null) intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon_bitmap);
		else {
			final Context pkg_context = context.createPackageContext(pkg, 0);
			final int icon = activity.icon != 0 ? activity.icon : activity.applicationInfo.icon;
			intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(pkg_context, icon));
		}
		intent.putExtra("duplicate", false);		// Special extra to prevent duplicate shortcut being created
		return intent;
	}

	/** Runs in managed profile or device owner */
	private boolean launchApp(final Intent intent) {
		final Uri uri = intent.getData();
		if (uri == null) return false;

		final ComponentName component = ComponentName.unflattenFromString(uri.getSchemeSpecificPart());
		if (! prepareToLaunchApp(component)) return false;

		final UserHandle user = Process.myUserHandle();
		final LauncherApps launcher = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
		try {
			launcher.startMainActivity(component, user, intent.getSourceBounds(), null);
		} catch (final NullPointerException e) {	// A known bug in LauncherAppsService when activity is not found
			Log.w(TAG, "Not found in Island: " + component);
			return false;
		}
		return launcher.isActivityEnabled(component, user);
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
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

	private static Bitmap drawableToBitmap(final Drawable d) {
		if (d instanceof BitmapDrawable)
			return ((BitmapDrawable) d).getBitmap();
		if (d instanceof ColorDrawable) //TODO: Support color drawable
			return null;
		final Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		d.draw(canvas);
		return bitmap;
	}

	private static final String TAG = "AppShortcut";
}
