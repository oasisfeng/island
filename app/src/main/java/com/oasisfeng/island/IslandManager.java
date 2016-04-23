package com.oasisfeng.island;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.data.AppListViewModel;
import com.oasisfeng.island.data.AppViewModel.State;
import com.oasisfeng.island.provisioning.ProfileOwnerSystemProvisioning;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.USER_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
import static android.content.pm.PackageManager.GET_PROVIDERS;
import static android.content.pm.PackageManager.GET_SERVICES;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.ProviderInfo.FLAG_SINGLE_USER;

/**
 * The engine of Island
 *
 * Created by Oasis on 2016/4/5.
 */
public class IslandManager implements AppListViewModel.Controller {

	public static final int REQUEST_CODE_INSTALL = 0x101;
	/** Provision state: 1 - Managed profile provision is completed, 2 - Island provision is started, 3 - Island provision is completed */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";
	private static final Collection<String> sCriticalSystemPkgs = Arrays.asList(
			"android", "com.android.systemui",		// There packages are generally safe to either freeze or not, leave them unfrozen for better compatibility.
			"com.android.packageinstaller",			// Package installer responsible for ACTION_INSTALL_PACKAGE
			"com.android.settings",					// For various setting intent activities
			"com.android.keychain",					// MIUI system will crash without this
			"com.android.providers.telephony",		// The SMS & MMS and carrier info provider. (SMS & MMS provider is shared across all users)
			"com.android.providers.contacts",		// The storage of contacts, for Contacts app to work
			"com.android.providers.downloads",		// For DownloadManager to work
			"com.android.providers.media",			// For media access
			"com.android.externalstorage",			// Documents provider - Internal storage
			"com.android.defconatiner",				// For APK file from DownloadManager to install.
			"com.android.documentsui",				// Document picker
			// Google packages
			"com.google.android.gsf",				// Google services framework
			"com.google.android.packageinstaller",	// Google's variant of package installer on Nexus devices
			"com.google.android.gms",				// Disabling GMS in the provision will cause GMS in owner user being killed too due to its single user nature, causing weird ANR.
			"com.google.android.feedback",			// Used by GMS for crash report.
			"com.google.android.webview",			// Standalone Chromium WebView
			// Enabled system apps with launcher activity by default
			"com.android.vending",					// Google Play Store to let user install apps directly within
			"com.android.contacts",					// Contacts
			"com.android.providers.downloads.ui"	// Downloads
	);
	private static final Collection<Intent> sCriticalActivityIntents = Arrays.asList(
			new Intent(Intent.ACTION_INSTALL_PACKAGE),				// Usually com.android.packageinstaller, may be altered by ROM.
			new Intent(Settings.ACTION_SETTINGS),					// Usually com.android.settings
			new Intent("android.content.pm.action.REQUEST_PERMISSIONS"),	// Hidden PackageManager.ACTION_REQUEST_PERMISSIONS
																	// Runtime permission UI, may be special system app on some ROMs (e.g. MIUI)
			new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),	// Usually com.android.documentsui, may be file explorer app on some ROMs. (e.g. MIUI)
			new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("*/*"),
			new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI),	// Contact picker, usually com.android.contacts
			new Intent(Intent.ACTION_VIEW, Uri.parse("http://g.cn"))// Web browser (required by some apps to open web link)
	);
	private static final Collection<String> sCriticalContentAuthorities = Arrays.asList(
			ContactsContract.AUTHORITY,				// Usually com.android.providers.contacts
			CalendarContract.AUTHORITY,				// Usually com.android.providers.calendar
			CallLog.AUTHORITY,						// Usually com.android.providers.contacts (originally com.android.providers.calllogbackup)
			Telephony.Carriers.CONTENT_URI.getAuthority(),	// Usually com.android.providers.telephony
			"com.android.externalstorage.documents",// Usually com.android.externalstorage
			"downloads",							// Usually com.android.providers.downloads
			"media"									// Usually com.android.providers.media
	);

	public IslandManager(final Context context) {
		mContext = context;
		mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
		mAdminComp = IslandDeviceAdminReceiver.getComponentName(context);
	}

	public @Nullable ApplicationInfo getAppInfo(String pkg) {
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
		if ((app_info.flags & FLAG_INSTALLED) != 0) {
			Log.w(TAG, "Already cloned: " + pkg);
			return;
		}

		// System apps can be enabled by DevicePolicyManager.enableSystemApp(), which calls installExistingPackage().
		if ((app_info.flags & (FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP)) != 0) {
			mDevicePolicyManager.enableSystemApp(mAdminComp, pkg);
			return;
		}

		// For non-system app, we initiate the manual installation process.
		ensureSystemAppEnabled("com.android.packageinstaller");
		ensureSystemAppEnabled("com.google.android.packageinstaller");
		try {
			mDevicePolicyManager.setSecureSetting(mAdminComp, Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
		} catch (final SecurityException e) {
			Log.e(TAG, "Failed to enable " + Settings.Secure.INSTALL_NON_MARKET_APPS + ": " + e);
		}
		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, mContext.getPackageName());

		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null)
			activity.startActivityForResult(intent.putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_CODE_INSTALL);
		else mContext.startActivity(intent);
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
		final LauncherApps launcher_apps = (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		launcher_apps.startAppDetailsActivity(new ComponentName(pkg, ""), Process.myUserHandle(), null, null);
	}

	@Override public CharSequence readAppName(final String pkg) throws PackageManager.NameNotFoundException {
		final PackageManager pm = mContext.getPackageManager();
		@SuppressWarnings("WrongConstant") final ApplicationInfo info = pm.getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
		return info.loadLabel(pm);
	}


	public void destroy() {
		final String pkg = mContext.getPackageName();
		if (mDevicePolicyManager.isDeviceOwnerApp(pkg)) {
			new AlertDialog.Builder(mContext).setTitle("WARNING")
					.setMessage("Are you sure to deactivate Island?\n\nYou have to go through the setup flow again to bring it back.")
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> deactivateDeviceOwner()).show();
		} else if (mDevicePolicyManager.isProfileOwnerApp(pkg)) {
			new AlertDialog.Builder(mContext).setTitle("WARNING")
					.setMessage(R.string.dialog_destroy_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> removeProfileOwner()).show();
		}
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

	@SuppressLint("CommitPrefEdits") void onProfileProvisioningComplete() {
		Log.d(TAG, "onProfileProvisioningComplete");
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).commit();
		startProfileOwnerIslandProvisioning();
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();
		MainActivity.startInManagedProfile(mContext, android.os.Process.myUserHandle());
	}

	@SuppressLint("CommitPrefEdits") public void startProfileOwnerProvisioningIfNeeded() {
		if (mDevicePolicyManager.isDeviceOwnerApp(mContext.getPackageName())) return;	// Do nothing for device owner
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		final int state = prefs.getInt(PREF_KEY_PROVISION_STATE, 0);
		if (state >= 3) return;		// Already provisioned
		if (state == 2) {
			Log.w(TAG, "Last provision attempt failed, no more attempts...");
			return;		// Last attempt failed again, no more attempts.
		} else if (state == 1) Log.w(TAG, "Last provision attempt might be interrupted, try provisioning one more time...");
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 2).commit();	// Avoid further attempts

		if (state == 0)		// Managed profile provision was not performed, the profile may be enabled manually.
			ProfileOwnerSystemProvisioning.start(mDevicePolicyManager, mAdminComp);	// Simulate the stock managed profile provision

		startProfileOwnerIslandProvisioning();	// Last provision attempt may be interrupted

		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();
	}

	private void startProfileOwnerIslandProvisioning() {
		disableNonCriticalSystemApps();
		enableRequiredSystemApps();		// Enable system apps responsible for required intents. (package name unspecified)

		enableProfile();
		enableAdditionalForwarding();
	}

	/** This is generally not necessary on AOSP but will eliminate various problems on custom ROMs */
	private void disableNonCriticalSystemApps() {
		final PackageManager pm = mContext.getPackageManager();
		final Set<String> critical_sys_pkgs = new HashSet<>(sCriticalSystemPkgs);
		// Detect package names for critical intent actions, as an addition to the whitelist of well-known ones.
		for (final Intent intent : sCriticalActivityIntents) {
			mDevicePolicyManager.enableSystemApp(mAdminComp, intent);
			final List<ResolveInfo> activities = pm.queryIntentActivities(intent, MATCH_DEFAULT_ONLY);
			FluentIterable.from(activities).filter(info -> (info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) != 0)
					.transform(info -> info.activityInfo.packageName)
					.filter(pkg -> { Log.i(TAG, "Critical package for " + intent + ": " + pkg); return true; })
					.copyInto(critical_sys_pkgs);
		}
		// Detect package names for critical content providers, as an addition to the whitelist of well-known ones.
		for (final String authority : sCriticalContentAuthorities) {
			final ProviderInfo provider = pm.resolveContentProvider(authority, 0);
			if (provider == null || (provider.applicationInfo.flags & FLAG_SYSTEM) == 0 || (provider.flags & FLAG_SINGLE_USER) != 0) continue;
			Log.i(TAG, "Critical package for authority \"" + authority + "\": " + provider.packageName);
			critical_sys_pkgs.add(provider.packageName);
		}

		final List<ApplicationInfo> apps = pm.getInstalledApplications(0);
		final ImmutableList<ApplicationInfo> sys_apps_to_hide = FluentIterable.from(apps)
				.filter(IslandManager::isSystemApp)
				.filter(info -> ! critical_sys_pkgs.contains(info.packageName))
				.toList();
		for (final ApplicationInfo app : sys_apps_to_hide) {
			if (hasSingleUserComponent(pm, app.packageName)) {
				Log.i(TAG, "Not disabling system app capable for multi-user: " + app.packageName);
			} else {
				Log.i(TAG, "Disable non-critical system app: " + app.packageName);
				freezeApp(app.packageName);
			}
		}
	}

	private boolean hasSingleUserComponent(final PackageManager pm, final String pkg) {
		try {
			final PackageInfo info = pm.getPackageInfo(pkg, GET_SERVICES | GET_PROVIDERS);
			if (info.services != null) for (final ServiceInfo service : info.services)
				if ((service.flags & ServiceInfo.FLAG_SINGLE_USER) != 0) return true;
			if (info.providers != null) for (final ProviderInfo service : info.providers)
				if ((service.flags & ProviderInfo.FLAG_SINGLE_USER) != 0) return true;
			return false;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	private static boolean isSystemApp(final ApplicationInfo info) { return (info.flags & FLAG_SYSTEM) != 0; }

	private void enableRequiredSystemApps() {

		mDevicePolicyManager.enableSystemApp(mAdminComp, new Intent(Intent.ACTION_INSTALL_PACKAGE));
	}

	private void enableProfile() {
		Log.d(TAG, "Enable profile now.");
		mDevicePolicyManager.setProfileName(mAdminComp, mContext.getString(R.string.profile_name));
		// Enable the profile here, launcher will show all apps inside.
		mDevicePolicyManager.setProfileEnabled(mAdminComp);
	}

	private void enableAdditionalForwarding() {
		// For sharing across Island (bidirectional)
		enableForwarding(new IntentFilter(Intent.ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		final IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		browser.addDataScheme("http"); browser.addDataScheme("https"); browser.addDataScheme("ftp");
		enableForwarding(browser, FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	IslandManager enableForwarding(final IntentFilter filter, final int flags) {
		mDevicePolicyManager.addCrossProfileIntentFilter(mAdminComp, filter, flags);
		return this;
	}

	private final Context mContext;
	private final DevicePolicyManager mDevicePolicyManager;
	private final ComponentName mAdminComp;

	private static final String TAG = IslandManager.class.getSimpleName();
}
