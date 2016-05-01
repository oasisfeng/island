package com.oasisfeng.island.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Supplier;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.island.IslandDeviceAdminReceiver;
import com.oasisfeng.island.R;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.USER_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManager implements AppListViewModel.Controller {

	public static final int REQUEST_CODE_INSTALL = 0x101;

	public IslandManager(final Context context) {
		mContext = context;
		mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
		mAdminComp = IslandDeviceAdminReceiver.getComponentName(context);
		mLauncherApps = () -> (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
	}

	public @Nullable ApplicationInfo getAppInfo(final String pkg) {
		try { @SuppressWarnings("WrongConstant")
		final ApplicationInfo app_info = mContext.getPackageManager().getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
			return app_info;
		} catch (final PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public State getAppState(final ApplicationInfo app_info) {
		if ((app_info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) return State.NotCloned;
		if (! app_info.enabled) return State.Disabled;
		if (mDevicePolicyManager.isApplicationHidden(mAdminComp, app_info.packageName))
			return State.Frozen;
		return State.Alive;
	}

	@Override public void freezeApp(final String pkg) {
		mDevicePolicyManager.setApplicationHidden(mAdminComp, pkg, true);
	}

	@Override public void defreezeApp(final String pkg) {
		mDevicePolicyManager.setApplicationHidden(mAdminComp, pkg, false);
	}

	@Override public void launchApp(final String pkg) {
		final Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(pkg);
		if (intent == null) {
			Toast.makeText(mContext, "This app has no launch entrance.", Toast.LENGTH_SHORT).show();
			return;
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		mContext.startActivity(intent);
	}

	@Override public void cloneApp(final String pkg) {
		final PackageManager pm = mContext.getPackageManager();
		try { @SuppressWarnings("WrongConstant")
			final ApplicationInfo info = pm.getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
			cloneApp(info);
		} catch (final PackageManager.NameNotFoundException ignored) {}
	}

	private void cloneApp(final ApplicationInfo app_info) {
		final String pkg = app_info.packageName;
		if ((app_info.flags & FLAG_INSTALLED) != 0) { Log.e(TAG, "Already cloned: " + pkg); return; }

		// System apps can be enabled by DevicePolicyManager.enableSystemApp(), which calls installExistingPackage().
		if ((app_info.flags & FLAG_SYSTEM) != 0) {
			enableSystemApp(pkg);
			return;
		}
		/* For non-system app, we initiate the manual installation process. */

		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, mContext.getPackageName());
		enableSystemAppForActivity(intent);		// Ensure package installer is enabled.
		// Blindly clear these restrictions
		mDevicePolicyManager.clearUserRestriction(mAdminComp, UserManager.DISALLOW_INSTALL_APPS);
		mDevicePolicyManager.clearUserRestriction(mAdminComp, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);

		if (ensureInstallNonMarketAppAllowed()) {		// Launch package installer
			if (Scopes.app(mContext).mark("clone-via-install-explained")) {
				new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_install_explanation)
						.setPositiveButton(R.string.dialog_button_continue, (d, w) -> cloneApp(app_info)).show();
				return;
			}
			final Activity activity = Activities.findActivityFrom(mContext);
			if (activity == null) mContext.startActivity(intent);
			else activity.startActivityForResult(intent.putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_CODE_INSTALL);
		} else {										// Launch market app (preferable Google Play Store)
			final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
			enableSystemAppForActivity(market_intent);
			final ActivityInfo market_info = market_intent.resolveActivityInfo(mContext.getPackageManager(), 0);
			if ((market_info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
				new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_google_play_explanation)
						.setPositiveButton(R.string.dialog_button_continue, (d, w) -> cloneApp(pkg)).show();
				return;		// TODO: Analytics
			} else if (SystemAppsManager.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.applicationInfo.packageName)) {
				if (Scopes.app(mContext).mark("clone-via-google-play-explained")) {
					new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_google_play_explanation)
							.setPositiveButton(R.string.dialog_button_continue, (d, w) -> cloneApp(pkg)).show();
					return;
				}
				enableSystemApp(SystemAppsManager.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
			} else {
				if (Scopes.app(mContext).mark("clone-via-builtin-market-explained")) {
					new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_builtin_market_explanation)
							.setPositiveButton(R.string.dialog_button_continue, (d, w) -> cloneApp(pkg)).show();
					return;
				}
			}
			mContext.startActivity(market_intent);
		}
	}

	private boolean ensureInstallNonMarketAppAllowed() {
		if (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0) return true;
		// We cannot directly enable this secure setting on Android 5.0.x.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return false;

		mDevicePolicyManager.setSecureSetting(mAdminComp, Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
		return Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
	}

	private void ensureSystemAppEnabled(final String pkg) {
		try { @SuppressWarnings("WrongConstant")
			final ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
			if ((info.flags & FLAG_INSTALLED) != 0) defreezeApp(pkg);
			else cloneApp(info);
		} catch (final PackageManager.NameNotFoundException ignored) {}
	}

	@Override public void enableApp(final String pkg) {
		showAppSettingActivity(pkg);
	}

	@Override public void removeClone(final String pkg) {
		final int flags;
		try { @SuppressWarnings("WrongConstant")
			final ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
			flags = info.flags;
		} catch (final PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Try to remove non-existent clone: " + pkg);
			return;
		}
		if ((flags & FLAG_SYSTEM) != 0) {
			defreezeApp(pkg);	// App must not be hidden for startAppDetailsActivity() to work.
			showAppSettingActivity(pkg);
			return;
		}
		mContext.startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.fromParts("package", pkg, null)));
	}

	private void showAppSettingActivity(final String pkg) {
		ensureSystemAppEnabled("com.android.settings");
		mLauncherApps.get().startAppDetailsActivity(new ComponentName(pkg, ""), Process.myUserHandle(), null, null);
	}

	@Override public CharSequence readAppName(final String pkg) throws PackageManager.NameNotFoundException {
		final PackageManager pm = mContext.getPackageManager();
		@SuppressWarnings("WrongConstant") final ApplicationInfo info = pm.getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
		return info.loadLabel(pm);
	}

	@Override public boolean isCloneExclusive(final String pkg) {
		return ! mLauncherApps.get().isPackageEnabled(pkg, OWNER);
	}

	@Override public void createShortcut(final String pkg) {
		final ApplicationInfo info = getAppInfo(pkg);
		boolean need_freeze = false;
		if (info != null && getAppState(info) == State.Frozen) {
			defreezeApp(pkg);
			need_freeze = true;
		}

		if (AppLaunchShortcut.createOnLauncher(mContext, pkg)) {
			Toast.makeText(mContext, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();
		} else Toast.makeText(mContext, R.string.toast_shortcut_failed, Toast.LENGTH_SHORT).show();

		if (need_freeze) new Handler().postDelayed(() -> freezeApp(pkg), 500);	// TODO: Elegant dealing
	}


	public void destroy() {
		if (Process.myUserHandle().hashCode() == 0 && isDeviceOwner()) {
			new AlertDialog.Builder(mContext).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_deactivate_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> deactivateDeviceOwner()).show();
		} else if (Process.myUserHandle().hashCode() != 0 && isProfileOwner() && isProfileOwnerActive()) {
			new AlertDialog.Builder(mContext).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_destroy_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> removeProfileOwner()).show();
		} else new AlertDialog.Builder(mContext).setMessage(R.string.dialog_destroy_failure_message)
					.setNegativeButton(android.R.string.ok, null).show();
	}

	private void removeProfileOwner() {
		if (mDevicePolicyManager.isProfileOwnerApp(mContext.getPackageName()))		// Ensure we are just wiping managed profile, not the primary user
			mDevicePolicyManager.wipeData(0);
		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null) activity.finish();
	}

	private void deactivateDeviceOwner() {
		mDevicePolicyManager.clearDeviceOwnerApp(mContext.getPackageName());
		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null) activity.finish();
	}

	public boolean isDeviceOwner() {
		return mDevicePolicyManager.isDeviceOwnerApp(mContext.getPackageName());
	}

	public boolean isProfileOwner() {
		return mDevicePolicyManager.isProfileOwnerApp(mContext.getPackageName());
	}

	public boolean isProfileOwnerActive() {
		if (Process.myUserHandle().hashCode() == 0) throw new IllegalStateException("Must not be called in owner user");
		return mDevicePolicyManager.isAdminActive(mAdminComp);
	}

	public static @Nullable UserHandle getManagedProfile(final Context context) {
		final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
		final List<UserHandle> profiles = um.getUserProfiles();
		for (final UserHandle profile : profiles)
			if (! profile.equals(Process.myUserHandle())) return profile;   // Only one managed profile is supported by Android at present.
		return null;
	}

	/** @return the profile owner component, null for none or failure */
	public static @Nullable ComponentName getProfileOwner(final Context context, final UserHandle profile) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
		try {
			return (ComponentName) DevicePolicyManager.class.getMethod("getProfileOwnerAsUser", int.class).invoke(dpm, profile.hashCode());
		} catch (final NoSuchMethodException | IllegalAccessException e) {
			Log.e(TAG, "Partially incompatible ROM: No public method - DevicePolicyManager.getProfileOwnerAsUser()");
		} catch (final InvocationTargetException | SecurityException e) {
			Log.e(TAG, "Failed to get profile owner of user " + profile.hashCode(), e);
		}
		return null;
	}

	public void enableProfile() {
		Log.d(TAG, "Enable profile now.");
		mDevicePolicyManager.setProfileName(mAdminComp, mContext.getString(R.string.profile_name));
		// Enable the profile here, launcher will show all apps inside.
		mDevicePolicyManager.setProfileEnabled(mAdminComp);
	}

	public IslandManager enableForwarding(final IntentFilter filter, final int flags) {
		mDevicePolicyManager.addCrossProfileIntentFilter(mAdminComp, filter, flags);
		return this;
	}

	public void enableSystemAppForActivity(final Intent intent) {
		try {
			final int result = mDevicePolicyManager.enableSystemApp(mAdminComp, intent);
			if (result > 0) Log.d(TAG, result + " system apps enabled for: " + intent);
		} catch (final IllegalArgumentException e) {
			// This exception may be thrown on Android 5.x (but not 6.0+) if non-system apps also match this intent.
			// System apps should have been enabled before this exception is thrown, so we just ignore it.
			Log.w(TAG, "System apps may not be enabled for: " + intent);
		}
	}

	private void enableSystemApp(final String pkg) {
		try {
			mDevicePolicyManager.enableSystemApp(mAdminComp, pkg);
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "Failed to enable: " + pkg, e);
		}
	}

	public boolean isLaunchable(final ApplicationInfo app) {
		final String pkg = app.packageName;
		final LauncherApps launcher = mLauncherApps.get();
		return mCurrentUser.equals(OWNER) || getAppState(app) == State.Alive ? ! launcher.getActivityList(pkg, mCurrentUser).isEmpty()
				: ! launcher.getActivityList(pkg, OWNER).isEmpty();		// Detect launcher activity in primary user if not alive
	}

	private final Context mContext;
	private final DevicePolicyManager mDevicePolicyManager;
	private final ComponentName mAdminComp;
	private final Supplier<LauncherApps> mLauncherApps;
	private static final UserHandle mCurrentUser = Process.myUserHandle();

	private static final UserHandle OWNER;
	static {
		final Parcel p = Parcel.obtain();
		try {
			p.writeInt(0);
			p.setDataPosition(0);
			OWNER = new UserHandle(p);
		} finally {
			p.recycle();
		}
	}
	private static final String TAG = IslandManager.class.getSimpleName();
}
