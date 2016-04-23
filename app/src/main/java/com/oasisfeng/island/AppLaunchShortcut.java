package com.oasisfeng.island;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

/**
 * Launch shortcut for apps on Island, from the owner user space
 *
 * Created by Oasis on 2016/4/22.
 */
public class AppLaunchShortcut extends Activity {

	private static final String ACTION_INSTALL_SHORTCUT = "com.oasisfeng.island.action.INSTALL_SHORTCUT";
	private static final String ACTION_LAUNCH_APP = "com.oasisfeng.island.action.LAUNCH_APP";

	/** Runs in owner user space to delegate the shortcut install broadcast */
	public static class Installer extends Activity {

		@Override protected void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			onNewIntent(getIntent());
		}

		@Override protected void onNewIntent(final Intent intent) {
			final ComponentName launchpad = new ComponentName(this, AppLaunchShortcut.class);
			// Disable launchpad in this user to make sure only the one in managed profile receives this intent
			getPackageManager().setComponentEnabledSetting(launchpad, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);

			final Intent install = new Intent().putExtras(intent.getExtras());
			sendBroadcast(install.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT"));
			sendBroadcast(install.setAction("com.android.launcher.action.INSTALL_SHORTCUT"));
			finish();
		}
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		onNewIntent(getIntent());
	}

	/** Must be called in managed profile */
	static boolean createOnLauncher(final Context context, final String pkg) throws PackageManager.NameNotFoundException {
		final PackageManager pm = context.getPackageManager();
		final Intent target = pm.getLaunchIntentForPackage(pkg);
		if (target == null) return false;
		target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

		final Intent launch_intent = new Intent(ACTION_LAUNCH_APP)
				.setData(Uri.fromParts("target", target.getComponent().flattenToShortString(), null));
		if (target.hasCategory(Intent.CATEGORY_INFO)) launch_intent.addCategory(Intent.CATEGORY_INFO);
		if (target.hasCategory(Intent.CATEGORY_LAUNCHER)) launch_intent.addCategory(Intent.CATEGORY_LAUNCHER);

		final Intent shortcut = new Intent(ACTION_INSTALL_SHORTCUT);	// Extras will be carried in broadcast by Installer.
		shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch_intent);
		@SuppressWarnings("WrongConstant") final ActivityInfo activity = pm.getActivityInfo(target.getComponent(), 0);
		shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, activity.loadLabel(pm));	// TODO: Unicode lock char: \uD83D\uDD12
		final Bitmap icon_bitmap = drawableToBitmap(pm.getActivityIcon(target.getComponent()));
		if (icon_bitmap != null) shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon_bitmap);
		else {
			final Context pkg_context = context.createPackageContext(pkg, 0);
			final int icon = activity.icon != 0 ? activity.icon : activity.applicationInfo.icon;
			shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(pkg_context, icon));
		}
		shortcut.putExtra("duplicate", false);

		final IntentFilter installer_filter = new IntentFilter(ACTION_INSTALL_SHORTCUT);
		installer_filter.addCategory(Intent.CATEGORY_DEFAULT);
		final IntentFilter launchpad_filter = new IntentFilter(ACTION_LAUNCH_APP);
		launchpad_filter.addDataScheme("target");
		launchpad_filter.addCategory(Intent.CATEGORY_DEFAULT);
		launchpad_filter.addCategory(Intent.CATEGORY_LAUNCHER);
		launchpad_filter.addCategory(Intent.CATEGORY_INFO);
		new IslandManager(context).enableForwarding(installer_filter, FLAG_PARENT_CAN_ACCESS_MANAGED)
				.enableForwarding(launchpad_filter, FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Disable installer in managed profile to make sure only the one in owner user receives this intent
		final ComponentName installer = new ComponentName(context, Installer.class);
		pm.setComponentEnabledSetting(installer, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		try {
			context.startActivity(shortcut);
			return true;
		} catch (final ActivityNotFoundException e) {
			Log.e(TAG, "Failed to send shortcut installation request.");
			return false;
		}
	}

	/** Runs in managed profile */
	private void launchApp(final Intent intent) {
		final Uri uri = intent.getData();
		if (uri == null) return;
		final ComponentName component = ComponentName.unflattenFromString(uri.getSchemeSpecificPart());

		// Ensure de-frozen
		new IslandManager(this).defreezeApp(component.getPackageName());

		final LauncherApps launcher = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
		final UserHandle user = android.os.Process.myUserHandle();
		launcher.startMainActivity(component, user, intent.getSourceBounds(), null);
		if (! launcher.isActivityEnabled(component, user))
			Toast.makeText(this, "Shortcut is no longer valid, please re-create it in Island", Toast.LENGTH_LONG).show();
	}

	/** Must be called in owner user */
	boolean createOnLauncherAsOwner(final Intent intent) {
		final Intent shortcut = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
		sendBroadcast(shortcut.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT"));
		sendBroadcast(shortcut.setAction("com.android.launcher.action.INSTALL_SHORTCUT"));
		return true;
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
		if (ACTION_LAUNCH_APP.equals(action)) {
			launchApp(intent);
		} else if (ACTION_INSTALL_SHORTCUT.equals(action)) {
			createOnLauncherAsOwner(intent);
		}
	}

	public static Bitmap drawableToBitmap(final Drawable d) {
		if (d instanceof BitmapDrawable)
			return ((BitmapDrawable) d).getBitmap();
		if (d instanceof ColorDrawable) //TODO: working to support color drawable
			return null;
		final Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		d.draw(canvas);
		return bitmap;
	}

	private static final String TAG = "AppShortcut";
}
