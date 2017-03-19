package com.oasisfeng.island.shortcut;

import android.app.Activity;
import android.content.BroadcastReceiver;
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

import com.oasisfeng.android.ui.IconResizer;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Users;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
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

	protected abstract boolean prepareToLaunchApp(final ComponentName component);

	public static boolean createOnLauncher(final Context context, final String pkg, final boolean owner, final String shortcut_prefix) {
		final PackageManager pm = context.getPackageManager();
		@SuppressWarnings("deprecation") final List<ResolveInfo> activities = pm.queryIntentActivities(
				new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), PackageManager.GET_UNINSTALLED_PACKAGES);
		if (activities.isEmpty()) {
			Analytics.$().event("shortcut_non_launchable").with(ITEM_ID, pkg).send();
			return false;
		}
		final ActivityInfo activity = activities.get(0).activityInfo;
		final ComponentName component = new ComponentName(activity.packageName, activity.name);
		final Intent launch_intent = new Intent(owner ? ACTION_LAUNCH_APP : ACTION_LAUNCH_CLONE).addCategory(Intent.CATEGORY_LAUNCHER)
				.setData(Uri.fromParts("target", component.flattenToShortString(), null));

		final Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch_intent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcut_prefix + activity.loadLabel(pm));
		intent.putExtra("duplicate", false);		// Special extra to prevent duplicate shortcut being created

		final ActivityInfo activity_info;
		try {
			activity_info = pm.getActivityInfo(component, PackageManager.GET_UNINSTALLED_PACKAGES);
		} catch (final NameNotFoundException e) {
			Analytics.$().report(e);
			return false;
		}
		// TODO: Load icon in higher density
//		final Resources res = pm.getResourcesForApplication(activity_info.applicationInfo);
//		final TypedValue value = new TypedValue();
//		final int icon_res = activity_info.getIconResource();
//		res.getValueForDensity(icon_res, higher_density, value, true);
//		app_icon = res.getDrawableForDensity(icon_res, higher_density, null);
		final Drawable app_icon = activity_info.loadIcon(pm);
		final Drawable icon = new IconResizer().createIconThumbnail(app_icon);	// In case the app icon is too large, to avoid TransactionTooLargeException.
		final Bitmap icon_bitmap = drawableToBitmap(owner ? icon : pm.getUserBadgedIcon(icon, Users.profile));
		if (icon_bitmap == null) {
			Analytics.$().event("shortcut_invalid_app_icon").with(ITEM_ID, pkg).with(ITEM_CATEGORY, app_icon.getClass().getName()).send();
			return false;
		}

		// Do not carry the heavy-weight bitmap payload in UNINSTALL_SHORTCUT broadcast, only in INSTALL_SHORTCUT broadcast.
		final BroadcastReceiver install_task = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent unused) {
			context.sendBroadcast(intent.setAction(ACTION_INSTALL_SHORTCUT).putExtra(Intent.EXTRA_SHORTCUT_ICON, icon_bitmap));
		}};
		try {
			context.sendOrderedBroadcast(intent.setAction(ACTION_UNINSTALL_SHORTCUT), null, install_task, null, Activity.RESULT_OK, null, null);
		} catch (final RuntimeException e) {
			install_task.onReceive(context, null);
		}
		return true;
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

	private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
	private static final String ACTION_UNINSTALL_SHORTCUT = "com.android.launcher.action.UNINSTALL_SHORTCUT";
	private static final String TAG = "AppShortcut";
}
