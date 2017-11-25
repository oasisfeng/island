package com.oasisfeng.island.provisioning;

import android.app.Notification;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.android.content.pm.Permissions;
import com.oasisfeng.island.InternalService;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.api.Api;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.shortcut.AbstractAppLaunchShortcut;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;

import java.util.Set;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.app.Notification.PRIORITY_HIGH;
import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.CATEGORY_BROWSABLE;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * The one-time provisioning for newly created managed profile of Island
 *
 * Created by Oasis on 2016/4/26.
 */
public abstract class IslandProvisioning extends InternalService.InternalIntentService {

	/**
	 * Provision state:
	 *   1 - Managed profile provision (stock) is completed
	 *   2 - Island provision is started, POST_PROVISION_REV - Island provision is completed.
	 *   [3,POST_PROVISION_REV> - Island provision is completed in previous version, but needs re-performing in this version.
	 *   POST_PROVISION_REV - Island provision is up-to-date, nothing to do.
	 */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";
	/** Provision type: 0 (default) - Managed provisioning, 1 - Manual provisioning */
	private static final String PREF_KEY_PROFILE_PROVISION_TYPE = "profile.provision.type";
	/** The revision for post-provisioning. Increase this const value if post-provisioning needs to be re-performed after upgrade. */
	private static final int POST_PROVISION_REV = 9;

	public static void start(final Context context, final @Nullable String action) {
		final Intent intent = new Intent(action).setComponent(getComponent(context, IslandProvisioning.class));
		if (SDK_INT >= O) context.startForegroundService(intent);
		else context.startService(intent);
	}

	/** This is the normal procedure after ManagedProvision finished its provisioning, running in profile. */
	@ProfileUser public static void onProfileProvisioningComplete(final Context context, final Intent intent) {
		Log.d(TAG, "onProfileProvisioningComplete");
		if (Users.isOwner()) return;		// Nothing to do for managed device provisioning.
		start(context, intent.getAction());
	}

	@ProfileUser @WorkerThread @Override protected void onHandleIntent(@Nullable final Intent intent) {
		if (intent == null) return;		// Should never happen since we already setIntentRedelivery(true).
		final DevicePolicies policies = new DevicePolicies(this);
		// Grant INTERACT_ACROSS_USERS & WRITE_SECURE_SETTINGS permission early, since they may be required in the following provision procedure.
		if (SDK_INT >= M) {		// Dev permission is always granted for all users.
			policies.setPermissionGrantState(getPackageName(), INTERACT_ACROSS_USERS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
			policies.setPermissionGrantState(getPackageName(), WRITE_SECURE_SETTINGS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
		}

		if (DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED.equals(intent.getAction())) {	// ACTION_DEVICE_OWNER_CHANGED is added in Android 6.
			Analytics.$().event("device_provision_manual_start").send();
			startDeviceOwnerPostProvisioning(this, policies);
			return;
		}

		final boolean is_manual_setup = Intent.ACTION_USER_INITIALIZE.equals(intent.getAction()) || intent.getAction() == null/* recovery procedure triggered by MainActivity */;
		final Analytics.Trace trace = Analytics.startTrace(is_manual_setup ? "Provision (Manual)" : "Provision (Managed)");
		Analytics.$().setProperty(Analytics.Property.IslandSetup, is_manual_setup ? "manual" : "managed");
		Log.d(TAG, "Provisioning profile (" + Users.toId(android.os.Process.myUserHandle()) + (is_manual_setup ? ", manual) " : ")"));

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).apply();
		if (is_manual_setup) {		// Do the similar job of ManagedProvisioning here.
			Log.d(TAG, "Manual provisioning");
			Analytics.$().event("profile_post_provision_manual_start").send();
			prefs.edit().putInt(PREF_KEY_PROFILE_PROVISION_TYPE, 1).apply();
			ProfileOwnerManualProvisioning.start(this, policies);	// Mimic the stock managed profile provision
		} else Analytics.$().event("profile_post_provision_start").send();

		Log.d(TAG, "Start post-provisioning.");
		try {
			startProfileOwnerPostProvisioning(this, policies);
		} catch (final Exception e) {
			Analytics.$().event("profile_post_provision_error").with(Analytics.Param.ITEM_NAME, e.toString()).send();
			Analytics.$().report(e);
		}

		// Prepare critical apps
		enableCriticalAppsIfNeeded(this, policies);

		if (! is_manual_setup) {	// Enable the profile here, launcher will show all apps inside.
			policies.setProfileName(getString(R.string.profile_name));
			Log.d(TAG, "Enable profile now.");
			policies.setProfileEnabled();
		}
		Analytics.$().event("profile_post_provision_done").send();

		disableLauncherActivity(this);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).apply();

		if (! launchMainActivityAsUser(this, Users.owner))
			Log.e(TAG, "Failed to launch main activity in owner user.");

		trace.stop();
	}

	@WorkerThread public static void performIncrementalProfileOwnerProvisioningIfNeeded(final Context context) {
		startProfileOwnerPostProvisioning(context, new DevicePolicies(context));
	}

	@Override public void onCreate() {
		super.onCreate();
		NotificationIds.Provisioning.startForeground(this, mForegroundNotification.get());
	}

	@Override public void onDestroy() {
		stopForeground(true);
		super.onDestroy();
	}

	private static boolean launchMainActivityAsUser(final Context context, final UserHandle user) {
		if (SDK_INT > N) return false;
		final LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		if (apps == null) return false;
		final ComponentName activity = Modules.getMainLaunchActivity(context);
		if (! apps.isActivityEnabled(activity, user))
			return false;    // Since Android O, activities in owner user in invisible to managed profile.
		apps.startMainActivity(activity, user, null, null);
		return true;
	}

	private static void disableLauncherActivity(final Context context) {		// To mark the finish of post-provisioning
		try {
			context.getPackageManager().setComponentEnabledSetting(Modules.getMainLaunchActivity(context), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		} catch (final SecurityException e) {
			// FIXME: No permission to alter another module.
		}
	}

	@ProfileUser private static void enableCriticalAppsIfNeeded(final Context context, final DevicePolicies policies) {
		final Set<String> pkgs = CriticalAppsManager.detectCriticalPackages(context.getPackageManager(), Hacks.MATCH_ANY_USER_AND_UNINSTALLED);
		for (final String pkg : pkgs) try {
			policies.enableSystemApp(pkg);        // FIXME: Don't re-enable explicitly cloned system apps. (see ClonedHiddenSystemApps)
			policies.setApplicationHidden(pkg, false);
		} catch (final IllegalArgumentException ignored) {}		// Ignore non-existent packages.
	}

	/** Re-provisioning triggered by user */
	@ProfileUser @WorkerThread public static void reprovision(final Context context) {
		Log.d(TAG, "Start re-provisioning.");

		final DevicePolicies policies = new DevicePolicies(context);
		if (Users.isOwner()) {
			startDeviceOwnerPostProvisioning(context, policies);
			return;
		}

		// Always perform all the required provisioning steps covered by stock ManagedProvisioning, in case something is missing there.
		// This is also required for manual provision via ADB shell.
		policies.clearCrossProfileIntentFilters();

		ProfileOwnerManualProvisioning.start(context, policies);	// Simulate the stock managed profile provision

		startProfileOwnerPostProvisioning(context, policies);

		disableLauncherActivity(context);
	}

	/** All the preparations after the provisioning procedure of system ManagedProvisioning */
	@OwnerUser public static void startDeviceOwnerPostProvisioning(final Context context, final DevicePolicies policies) {
		if (! policies.isDeviceOwner()) return;

		if (SDK_INT >= N) policies.clearUserRestrictionsIfNeeded(context, UserManager.DISALLOW_ADD_USER);
		try {
			if (SDK_INT >= N_MR1 && ! policies.isBackupServiceEnabled())
				policies.setBackupServiceEnabled(true);
		} catch (final SecurityException | IllegalStateException e) {	// "SecurityException: There should only be one user, managed by Device Owner" if more than 1 user exists. (only on N_MR1)
			Analytics.$().report(e);
		}
	}

	/** All the preparations after the provisioning procedure of system ManagedProvisioning, also shared by manual provisioning. */
	@ProfileUser @WorkerThread private static void startProfileOwnerPostProvisioning(final Context context, final DevicePolicies policies) {
		if (SDK_INT >= M) policies.addUserRestrictionIfNeeded(context, UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);
		ensureInstallNonMarketAppAllowed(context, policies);

		enableAdditionalForwarding(policies);

		// Prepare AppLaunchShortcut
		final IntentFilter launchpad_filter = new IntentFilter(AbstractAppLaunchShortcut.ACTION_LAUNCH_CLONE);
		launchpad_filter.addDataScheme("target");
		launchpad_filter.addCategory(Intent.CATEGORY_DEFAULT);
		launchpad_filter.addCategory(CATEGORY_LAUNCHER);
		policies.addCrossProfileIntentFilter(launchpad_filter, FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare ServiceShuttle
		policies.addCrossProfileIntentFilter(new IntentFilter(ServiceShuttle.ACTION_BIND_SERVICE), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare API
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(Api.latest.ACTION_FREEZE).withDataSchemes("package", "packages"), FLAG_MANAGED_CAN_ACCESS_PARENT);
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(Api.latest.ACTION_UNFREEZE).withDataSchemes("package", "packages"), FLAG_MANAGED_CAN_ACCESS_PARENT);
	}

	public static boolean ensureInstallNonMarketAppAllowed(final Context context, final DevicePolicies policies) {
		policies.clearUserRestrictionsIfNeeded(context, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);

		final ContentResolver resolver = context.getContentResolver();
		@SuppressWarnings("deprecation") final String INSTALL_NON_MARKET_APPS = Settings.Secure.INSTALL_NON_MARKET_APPS;
		if (Settings.Secure.getInt(resolver, INSTALL_NON_MARKET_APPS, 0) > 0) return true;
		if (SDK_INT < LOLLIPOP_MR1) {		// INSTALL_NON_MARKET_APPS is not whitelisted by DPM.setSecureSetting() until Android 5.1.
			if (! Permissions.has(context, WRITE_SECURE_SETTINGS)) return false;
			Settings.Secure.putInt(resolver, INSTALL_NON_MARKET_APPS, 1);
		} else policies.setSecureSetting(INSTALL_NON_MARKET_APPS, "1");
		return Settings.Secure.getInt(resolver, INSTALL_NON_MARKET_APPS, 0) > 0;
	}

	private static void enableAdditionalForwarding(final DevicePolicies policies) {
		// For sharing across Island (bidirectional)
		policies.addCrossProfileIntentFilter(new IntentFilter(ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(ACTION_VIEW).withCategory(CATEGORY_BROWSABLE).withDataSchemes("http", "https", "ftp"),
				FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	public IslandProvisioning() {
		super(IslandProvisioning.class);
		setIntentRedelivery(true);
	}

	private final Supplier<Notification.Builder> mForegroundNotification = Suppliers.memoize(() ->
			new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_notify_sync).setPriority(PRIORITY_HIGH).setUsesChronometer(true)
					.setContentTitle(getText(R.string.notification_provisioning_title))
					.setContentText(getText(R.string.notification_provisioning_text)));

	private static final String TAG = "Island.Provision";
}
