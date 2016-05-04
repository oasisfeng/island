package com.oasisfeng.island.shortcut;

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
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.island.R;
import com.oasisfeng.island.engine.IslandManager;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

/**
 * Launch shortcut for apps on Island, from the owner user space
 *
 * TODO: Secure both the installer and launch intent to protect again abuse.
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

	public static boolean createOnLauncher(final Context context, final String pkg) {
		final IslandManager island = new IslandManager(context);
		try {
			if (! island.isDeviceOwner())		// The complex flow for managed profile
				return createOnLauncherInManagedProfile(context, island, pkg);
			final Bundle shortcut_payload = buildShortcutPayload(context, pkg);
			if (shortcut_payload == null) return false;
			broadcastShortcutInstall(context, shortcut_payload);
			return true;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	/** Must be called in managed profile */
	private static boolean createOnLauncherInManagedProfile(final Context context, final IslandManager island, final String pkg) throws PackageManager.NameNotFoundException {
		// Make sure required forwarding rules are ready
		final IntentFilter installer_filter = new IntentFilter(ACTION_INSTALL_SHORTCUT);
		installer_filter.addCategory(Intent.CATEGORY_DEFAULT);
		final IntentFilter launchpad_filter = new IntentFilter(ACTION_LAUNCH_APP);
		launchpad_filter.addDataScheme("target");
		launchpad_filter.addCategory(Intent.CATEGORY_DEFAULT);
		launchpad_filter.addCategory(Intent.CATEGORY_LAUNCHER);
		launchpad_filter.addCategory(Intent.CATEGORY_INFO);
		island.enableForwarding(installer_filter, FLAG_PARENT_CAN_ACCESS_MANAGED)
				.enableForwarding(launchpad_filter, FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Disable installer in managed profile to make sure only the one in owner user receives this intent
		final ComponentName installer = new ComponentName(context, Installer.class);
		context.getPackageManager().setComponentEnabledSetting(installer, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);

		final Bundle shortcut_payload = buildShortcutPayload(context, pkg);
		if (shortcut_payload == null) return false;
		final Intent shortcut_request = new Intent(ACTION_INSTALL_SHORTCUT).putExtras(shortcut_payload);
		try {
			context.startActivity(shortcut_request);
			return true;
		} catch (final ActivityNotFoundException e) {
			Log.e(TAG, "Failed to send shortcut installation request.");
			return false;
		}
	}

	/** @return null if the given package has no launch entrance */
	private static @Nullable Bundle buildShortcutPayload(final Context context, final String pkg) throws PackageManager.NameNotFoundException {
		final PackageManager pm = context.getPackageManager();
		final Intent target = pm.getLaunchIntentForPackage(pkg);
		if (target == null) return null;
		target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

		final Intent launch_intent = new Intent(ACTION_LAUNCH_APP)
				.setData(Uri.fromParts("target", target.getComponent().flattenToShortString(), null));
		if (target.hasCategory(Intent.CATEGORY_INFO)) launch_intent.addCategory(Intent.CATEGORY_INFO);
		if (target.hasCategory(Intent.CATEGORY_LAUNCHER)) launch_intent.addCategory(Intent.CATEGORY_LAUNCHER);

		final Bundle payload = new Bundle();
		payload.putParcelable(Intent.EXTRA_SHORTCUT_INTENT, launch_intent);
		final ActivityInfo activity = pm.getActivityInfo(target.getComponent(), 0);
		payload.putCharSequence(Intent.EXTRA_SHORTCUT_NAME, "\uD83C\uDF00" + activity.loadLabel(pm));	// TODO: Unicode lock char: \uD83D\uDD12
		final Bitmap icon_bitmap = drawableToBitmap(pm.getActivityIcon(target.getComponent()));
		if (icon_bitmap != null) payload.putParcelable(Intent.EXTRA_SHORTCUT_ICON, icon_bitmap);
		else {
			final Context pkg_context = context.createPackageContext(pkg, 0);
			final int icon = activity.icon != 0 ? activity.icon : activity.applicationInfo.icon;
			payload.putParcelable(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(pkg_context, icon));
		}
		payload.putBoolean("duplicate", false);
		return payload;
	}

	private static void broadcastShortcutInstall(final Context context, final Bundle shortcut) {
		final Intent install = new Intent().putExtras(shortcut);
		context.sendBroadcast(install.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT"));
		context.sendBroadcast(install.setAction("com.android.launcher.action.INSTALL_SHORTCUT"));
	}

	/** Runs in managed profile or device owner */
	private boolean launchApp(final Intent intent) {
		final IslandManager island = new IslandManager(this);
		if (Process.myUserHandle().hashCode() == 0 && ! island.isDeviceOwner()) return false;
		final Uri uri = intent.getData();
		if (uri == null) return false;
		final ComponentName component = ComponentName.unflattenFromString(uri.getSchemeSpecificPart());

		// Ensure de-frozen
		island.defreezeApp(component.getPackageName());

		final LauncherApps launcher = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
		final UserHandle user = android.os.Process.myUserHandle();
		try {
			launcher.startMainActivity(component, user, intent.getSourceBounds(), null);
		} catch (final NullPointerException e) {	// A known bug in LauncherAppsService when activity is not found
			Log.w(TAG, "Not found in Island: " + component);
			return false;
		}
		return launcher.isActivityEnabled(component, user);
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
		if (! ACTION_LAUNCH_APP.equals(action)) return;
		if (! launchApp(intent))
			Toast.makeText(this, R.string.toast_shortcut_invalid, Toast.LENGTH_LONG).show();
	}

	private static Bitmap drawableToBitmap(final Drawable d) {
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
