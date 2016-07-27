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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.R;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.api.ApiActivity;
import com.oasisfeng.island.api.ApiTokenManager;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;
import com.oasisfeng.island.util.DevicePolicies;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.USER_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManager implements AppListViewModel.Controller {

	private static final int MAX_DESTROYING_APPS_LIST = 8;
	private static final int REQUEST_CODE_INSTALL = 0x101;
	private static final String GREENIFY_PKG = BuildConfig.DEBUG ? "com.oasisfeng.greenify.debug" : "com.oasisfeng.greenify";
	private static final int MIN_GREENIFY_VERSION = BuildConfig.DEBUG ? 208 : 215;	// TODO: The minimal version of Greenify with support for Island.

	public IslandManager(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
		mLauncherApps = Suppliers.memoize(() -> (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE));
	}

	public @Nullable ApplicationInfo getAppInfo(final String pkg) {
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
		final ApplicationInfo app_info = mContext.getPackageManager().getApplicationInfo(pkg,
				PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_DISABLED_COMPONENTS);
			return app_info;
		} catch (final PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public State getAppState(final ApplicationInfo app_info) {
		if ((app_info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) return State.NotCloned;
		if (! app_info.enabled) return State.Disabled;
		if (mDevicePolicies.isApplicationHidden(app_info.packageName))
			return State.Frozen;
		return State.Alive;
	}

	@Override public boolean freezeApp(final String pkg, final String reason) {
		Analytics.$().event("action-freeze").with("package", pkg).send();
		return mDevicePolicies.setApplicationHidden(pkg, true) || mDevicePolicies.isApplicationHidden(pkg);
	}

	@Override public boolean defreezeApp(final String pkg) {
		Analytics.$().event("action-defreeze").with("package", pkg).send();
		return mDevicePolicies.setApplicationHidden(pkg, false) || ! mDevicePolicies.isApplicationHidden(pkg);
	}

	@Override public void launchApp(final String pkg) {
		Analytics.$().event("action-launch").with("package", pkg).send();
		if (mDevicePolicies.isApplicationHidden(pkg)) {
			if (! mDevicePolicies.setApplicationHidden(pkg, false))
				if (! Apps.of(mContext).isInstalledInCurrentUser(pkg)) return;	// Not installed, just give up.
		}
		try {
			if (mDevicePolicies.isPackageSuspended(pkg))
				mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		} catch (final PackageManager.NameNotFoundException ignored) {
			return;
		}

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
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
			final ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			cloneApp(info);
		} catch (final PackageManager.NameNotFoundException ignored) {}
	}

	private void cloneApp(final ApplicationInfo app_info) {
		final String pkg = app_info.packageName;
		if ((app_info.flags & FLAG_INSTALLED) != 0) { Log.e(TAG, "Already cloned: " + pkg); return; }

		// System apps can be enabled by DevicePolicyManager.enableSystemApp(), which calls installExistingPackage().
		if ((app_info.flags & FLAG_SYSTEM) != 0) {
			Analytics.$().event("action-clone-sys").with("package", pkg).send();
			enableSystemApp(pkg);
			return;
		}
		/* For non-system app, we initiate the manual installation process. */

		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, mContext.getPackageName());
		enableSystemAppForActivity(intent);		// Ensure package installer is enabled.
		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_APPS);
		mDevicePolicies.clearUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);

		if (ensureInstallNonMarketAppAllowed()) {		// Launch package installer
			final String mark = "clone-via-install-explained";
			if (! Scopes.app(mContext).isMarked(mark)) {
				new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_install_explanation)
						.setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
							Scopes.app(mContext).mark(mark);
							cloneApp(app_info);
						}).show();
				return;
			}
			Analytics.$().event("action-clone-install").with("package", pkg).send();
			final Activity activity = Activities.findActivityFrom(mContext);
			if (ANDROID_N || activity == null) mContext.startActivity(intent);
			else activity.startActivityForResult(intent.putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_CODE_INSTALL);
		} else {										// Launch market app (preferable Google Play Store)
			final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
			enableSystemAppForActivity(market_intent);
			final ActivityInfo market_info = market_intent.resolveActivityInfo(mContext.getPackageManager(), 0);
			Analytics.$().setProperty("sys_market", market_info == null ? null : market_info.packageName);
			if (market_info == null || (market_info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
				new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_incapable_explanation)
						.setNeutralButton(R.string.dialog_button_learn_more, (d, w) -> WebContent.view(mContext, Config.URL_CANNOT_CLONE_EXPLAINED))
						.setPositiveButton(android.R.string.cancel, null).show();
				return;
			} else if (SystemAppsManager.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.applicationInfo.packageName)) {
				final String mark = "clone-via-google-play-explained";
				if (! Scopes.app(mContext).isMarked(mark)) {
					new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_google_play_explanation)
							.setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
								Scopes.app(mContext).mark(mark);
								cloneApp(pkg);
							}).show();
					return;
				}
				enableSystemApp(SystemAppsManager.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
				Analytics.$().event("action-clone").with("package", pkg).send();
			} else {
				final String mark = "clone-via-builtin-market-explained";
				if (! Scopes.app(mContext).isMarked(mark)) {
					new AlertDialog.Builder(mContext).setMessage(R.string.dialog_clone_via_builtin_market_explanation)
							.setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
								Scopes.app(mContext).mark(mark);
								cloneApp(pkg);
							}).show();
					return;
				}
				Analytics.$().event("action-clone").with("package", pkg).send();
			}
			mContext.startActivity(market_intent);
		}
	}

	private boolean ensureInstallNonMarketAppAllowed() {
		if (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0) return true;
		// We cannot directly enable this secure setting on Android 5.0.x.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return false;

		mDevicePolicies.setSecureSetting(Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
		return Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
	}

	private void ensureSystemAppEnabled(final String pkg) {
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
			final ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			if ((info.flags & FLAG_INSTALLED) != 0) defreezeApp(pkg);
			else cloneApp(info);
		} catch (final PackageManager.NameNotFoundException ignored) {}
	}

	@Override public void enableApp(final String pkg) {
		Analytics.$().event("action-enable").with("package", pkg).send();
		showAppSettingActivity(pkg);
	}

	@Override public void removeClone(final String pkg) {
		final int flags;
		try { @SuppressWarnings({"WrongConstant", "deprecation"})
			final ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			flags = info.flags;
		} catch (final PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Try to remove non-existent clone: " + pkg);
			return;
		}
		if ((flags & FLAG_SYSTEM) != 0) {
			defreezeApp(pkg);	// App must not be hidden for startAppDetailsActivity() to work.
			showAppSettingActivity(pkg);
			Analytics.$().event("action-disable-sys-app").with("package", pkg).send();
			return;
		}
		Analytics.$().event("action-uninstall").with("package", pkg).send();
		mContext.startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.fromParts("package", pkg, null)));
	}

	@Override public void installForOwner(final String pkg) {
		if (OWNER.equals(Process.myUserHandle())) return;
		// Forward the installation
		ActivityForwarder.startActivityAsOwner(mContext, mDevicePolicies, new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null)));
		Analytics.$().event("action-install-outside").with("package", pkg).send();
	}

	private void showAppSettingActivity(final String pkg) {
		ensureSystemAppEnabled("com.android.settings");
		mLauncherApps.get().startAppDetailsActivity(new ComponentName(pkg, ""), Process.myUserHandle(), null, null);
	}

	@Override public CharSequence readAppName(final String pkg) throws PackageManager.NameNotFoundException {
		final PackageManager pm = mContext.getPackageManager();
		@SuppressWarnings({"WrongConstant", "deprecation"}) final ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
		return info.loadLabel(pm);
	}

	@Override public boolean isCloneExclusive(final String pkg) {
		return ! mLauncherApps.get().isPackageEnabled(pkg, OWNER);
	}

	@Override public void createShortcut(final String pkg) {
		Analytics.$().event("action-create-shortcut").with("package", pkg).send();
		if (AppLaunchShortcut.createOnLauncher(mContext, pkg)) {
			Toast.makeText(mContext, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();
		} else Toast.makeText(mContext, R.string.toast_shortcut_failed, Toast.LENGTH_SHORT).show();
	}

	@Override public void greenify(final String pkg) {
		Analytics.$().event("action-greenify").with("package", pkg).send();
		final boolean greenify_installed = mLauncherApps.get().isPackageEnabled(GREENIFY_PKG, OWNER);
		int greenify_version = 0;
		if (greenify_installed) try {
			@SuppressWarnings({"WrongConstant", "deprecation"}) final PackageInfo info = mContext.getPackageManager()
					.getPackageInfo(GREENIFY_PKG, PackageManager.GET_UNINSTALLED_PACKAGES);
			greenify_version = info.versionCode;
		} catch (final PackageManager.NameNotFoundException ignored) {}
		final boolean unavailable_or_version_too_low = greenify_version < MIN_GREENIFY_VERSION;
		final String mark = "greenify-explained";
		if (unavailable_or_version_too_low || ! Scopes.app(mContext).isMarked(mark)) {
			String message = mContext.getString(R.string.dialog_greenify_explanation);
			if (greenify_installed && unavailable_or_version_too_low)
				message += "\n\n" + mContext.getString(R.string.dialog_greenify_version_too_low);
			final int button = ! greenify_installed ? R.string.dialog_button_install : unavailable_or_version_too_low ? R.string.dialog_button_upgrade : R.string.dialog_button_continue;
			new AlertDialog.Builder(mContext).setTitle(R.string.dialog_greenify_title).setMessage(message)
					.setPositiveButton(button, (d, w) -> {
						if (unavailable_or_version_too_low) {
							final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GREENIFY_PKG));
							ActivityForwarder.startActivityAsOwner(mContext, mDevicePolicies, intent);
						} else {
							Scopes.app(mContext).mark(mark);
							greenify(pkg);
						}
					}).show();
			return;
		}

		final long user_sn = ((UserManager) mContext.getSystemService(USER_SERVICE)).getSerialNumberForUser(Process.myUserHandle());
		final Intent intent = new Intent("com.oasisfeng.greenify.action.GREENIFY").setPackage(GREENIFY_PKG)
				.setData(Uri.fromParts("package", pkg, "u" + user_sn))
				.putExtra(ApiActivity.EXTRA_API_TOKEN, new ApiTokenManager(mContext).getToken(GREENIFY_PKG));
		// Enable API for Greenify in this profile
		final ComponentName api = new ComponentName(mContext, ApiActivity.class);
		final PackageManager pm = mContext.getPackageManager();
		// TODO: Ensure ApiActivity is also enabled after re-installation.
		if (pm.getComponentEnabledSetting(api) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
			pm.setComponentEnabledSetting(api, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
			mDevicePolicies.addCrossProfileIntentFilter(new IntentFilter(ApiActivity.ACTION_GET_APP_LIST), FLAG_MANAGED_CAN_ACCESS_PARENT);
			final IntentFilter filter = new IntentFilter(ApiActivity.ACTION_FREEZE);
			mDevicePolicies.addCrossProfileIntentFilter(filter, FLAG_MANAGED_CAN_ACCESS_PARENT);	// Batch freeze API without data
			filter.addDataScheme("package");
			mDevicePolicies.addCrossProfileIntentFilter(filter, FLAG_MANAGED_CAN_ACCESS_PARENT);	// Single freeze API with data
		}
		ActivityForwarder.startActivityAsOwner(mContext, mDevicePolicies, intent);
	}

	@Override public boolean block(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, true);
		return failed == null || failed.length == 0;
	}

	@Override public boolean unblock(final String pkg) {
		final String[] failed = mDevicePolicies.setPackagesSuspended(new String[] { pkg }, false);
		return failed == null || failed.length == 0;
	}

	public void destroy(final ImmutableList<CharSequence> exclusive_clones) {
		if (Process.myUserHandle().hashCode() == 0 && isDeviceOwner()) {
			new AlertDialog.Builder(mContext).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_deactivate_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> deactivateDeviceOwner()).show();
		} else if (Process.myUserHandle().hashCode() != 0 && isProfileOwner() && isProfileOwnerActive()) {
			new AlertDialog.Builder(mContext).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_destroy_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> {
						if (exclusive_clones.isEmpty()) {
							removeProfileOwner();
							return;
						}
						final String names = Joiner.on('\n').skipNulls().join(FluentIterable.from(exclusive_clones).limit(MAX_DESTROYING_APPS_LIST));
						final String names_ellipsis = exclusive_clones.size() <= MAX_DESTROYING_APPS_LIST ? names : names + "…\n";
						new AlertDialog.Builder(mContext).setTitle(R.string.dialog_title_warning)
								.setMessage(mContext.getString(R.string.dialog_destroy_exclusives_message, exclusive_clones.size(), names_ellipsis))
								.setNeutralButton(R.string.dialog_button_destroy, (dd, ww) -> removeProfileOwner())
								.setPositiveButton(android.R.string.no, null).show();
					}).show();
		} else {
			new AlertDialog.Builder(mContext).setMessage(R.string.dialog_cannot_destroy_message)
					.setNegativeButton(android.R.string.ok, null).show();
			Analytics.$().event("cannot-destroy").send();
		}
	}

	private void removeProfileOwner() {
		Analytics.$().event("action-destroy").send();
		if (mDevicePolicies.getManager().isProfileOwnerApp(mContext.getPackageName()))		// Ensure we are just wiping managed profile, not the primary user
			mDevicePolicies.getManager().wipeData(0);
		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null) activity.finish();
	}

	private void deactivateDeviceOwner() {
		Analytics.$().event("action-deactivate").send();
		mDevicePolicies.getManager().clearDeviceOwnerApp(mContext.getPackageName());
		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null) activity.finish();
	}

	public boolean isDeviceOwner() {
		return mDevicePolicies.getManager().isDeviceOwnerApp(mContext.getPackageName());
	}

	public boolean isProfileOwner() {
		return mDevicePolicies.getManager().isProfileOwnerApp(mContext.getPackageName());
	}

	public boolean isProfileOwnerActive() {
		if (Process.myUserHandle().hashCode() == 0) throw new IllegalStateException("Must not be called in owner user");
		return mDevicePolicies.isAdminActive();
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
		mDevicePolicies.setProfileName(mContext.getString(R.string.profile_name));
		// Enable the profile here, launcher will show all apps inside.
		mDevicePolicies.setProfileEnabled();
	}

	public IslandManager enableForwarding(final IntentFilter filter, final int flags) {
		mDevicePolicies.addCrossProfileIntentFilter(filter, flags);
		return this;
	}

	void enableSystemAppForActivity(final Intent intent) {
		try {
			final int result = mDevicePolicies.enableSystemApp(intent);
			if (result > 0) Log.d(TAG, result + " system apps enabled for: " + intent);
		} catch (final IllegalArgumentException e) {
			// This exception may be thrown on Android 5.x (but not 6.0+) if non-system apps also match this intent.
			// System apps should have been enabled before this exception is thrown, so we just ignore it.
			Log.w(TAG, "System apps may not be enabled for: " + intent);
		}
	}

	private void enableSystemApp(final String pkg) {
		try {
			mDevicePolicies.enableSystemApp(pkg);
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "Failed to enable: " + pkg, e);
		}
	}

	public boolean isLaunchable(final String pkg) {
		return isLaunchable(mContext, pkg);
	}

	private static boolean isLaunchable(final Context context, final String pkg) {
		final PackageManager pm = context.getPackageManager();
		final Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
		@SuppressWarnings({"WrongConstant", "deprecation"}) final ResolveInfo resolved
				= pm.resolveActivity(intent, PackageManager.GET_DISABLED_COMPONENTS | PackageManager.GET_UNINSTALLED_PACKAGES);
		return resolved != null;
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;
	private final Supplier<LauncherApps> mLauncherApps;

	private static final UserHandle OWNER = GlobalStatus.OWNER;
	private static final boolean ANDROID_N = "N".equals(Build.VERSION.CODENAME);
	private static final String TAG = IslandManager.class.getSimpleName();
}
