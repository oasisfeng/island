package com.oasisfeng.island.provisioning;

import android.app.Notification;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.InternalService;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.api.Api;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.shortcut.AbstractAppLaunchShortcut;
import com.oasisfeng.island.shuttle.ActivityShuttle;
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
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.CATEGORY_BROWSABLE;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
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
	private static final int POST_PROVISION_REV = 8;

	private static final String PREF_KEY_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION = "sys.apps.rev";
	/** The revision for critical system packages list. Increase this const value if the list of critical system packages are changed after upgrade. */
	private static final int UP_TO_DATE_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION = 1;

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

	@ProfileUser @Override protected void onHandleIntent(@Nullable final Intent intent) {
		if (intent == null) return;		// Should never happen since we already setIntentRedelivery(true).
		final DevicePolicies policies = new DevicePolicies(this);
		// Grant INTERACT_ACROSS_USERS & WRITE_SECURE_SETTINGS permission early, since they may be required in the following provision procedure.
		if (SDK_INT >= M) {		// Dev permission is always granted for all users.
			policies.setPermissionGrantState(getPackageName(), INTERACT_ACROSS_USERS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
			policies.setPermissionGrantState(getPackageName(), WRITE_SECURE_SETTINGS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
		}

		if (DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED.equals(intent.getAction())) {	// ACTION_DEVICE_OWNER_CHANGED is added in Android 6.
			startDeviceOwnerPostProvisioning(policies);
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

		try {
			startProfileOwnerPostProvisioning(this, policies, prefs);
		} catch (final Exception e) {
			Analytics.$().event("profile_post_provision_error").with(Analytics.Param.ITEM_NAME, e.toString()).send();
			Analytics.$().report(e);
		}

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

	public static void performIncrementalProvisoningIfNeeded(final Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final int state = prefs.getInt(PREF_KEY_PROVISION_STATE, 0);
		if (state < POST_PROVISION_REV) {
			startProfileOwnerPostProvisioning(context, new DevicePolicies(context), prefs);
		} else if (state > POST_PROVISION_REV)
			prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).apply();	// To avoid persistent inconsistency.
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
		final ComponentName activity = Modules.getMainLaunchActivity(context);
		if (SDK_INT <= N) {
			final LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
			if (! apps.isActivityEnabled(activity, user))
				return false;    // Since Android O, activities in owner user in invisible to managed profile.
			apps.startMainActivity(activity, user, null, null);
		} else
			ActivityShuttle.startActivityAsUser(context, new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setComponent(activity), Users.owner);
		return true;
	}

	private static void disableLauncherActivity(final Context context) {		// To mark the finish of post-provisioning
		try {
			context.getPackageManager().setComponentEnabledSetting(Modules.getMainLaunchActivity(context), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		} catch (final SecurityException e) {
			// FIXME: No permission to alter another module.
		}
	}

	@ProfileUser private static void enableCriticalAppsIfNeeded(final Context context, final DevicePolicies policies, final @Nullable SharedPreferences prefs) {
		if (prefs != null && checkRevision(prefs, PREF_KEY_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION, UP_TO_DATE_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION) == -1) return;
		final Set<String> pkgs = CriticalAppsManager.detectCriticalPackages(context.getPackageManager(), Hacks.MATCH_ANY_USER_AND_UNINSTALLED);
		for (final String pkg : pkgs) try {
			policies.enableSystemApp(pkg);        // FIXME: Don't re-enable explicitly cloned system apps. (see ClonedHiddenSystemApps)
			policies.setApplicationHidden(pkg, false);
		} catch (final IllegalArgumentException ignored) {}		// Ignore non-existent packages.
	}

	/** @return -1 if up to date, or current revision otherwise */
	private static int checkRevision(final SharedPreferences prefs, final String pref_key, final int up_to_date_revision) {
		final int revision = prefs.getInt(pref_key, 0);
		if (revision == up_to_date_revision) return -1;
		if (revision > up_to_date_revision) Log.e(TAG, "Revision of " + pref_key + ": " + revision + " beyond " + up_to_date_revision);
		prefs.edit().putInt(pref_key, up_to_date_revision).apply();
		return revision;
	}

	/** Re-provisioning triggered by user */
	@ProfileUser public static void reprovision(final Context context) {
		final DevicePolicies policies = new DevicePolicies(context);
		if (Users.isOwner()) {
			startDeviceOwnerPostProvisioning(policies);
			return;
		}

		// TODO: Check for disabled / frozen critical system apps in main UI.

		// Always perform all the required provisioning steps covered by stock ManagedProvisioning, in case something is missing there.
		// This is also required for manual provision via ADB shell.
		policies.clearCrossProfileIntentFilters();

		ProfileOwnerManualProvisioning.start(context, policies);	// Simulate the stock managed profile provision

		startProfileOwnerPostProvisioning(context, policies, null);

		disableLauncherActivity(context);
	}

	/** All the preparations after the provisioning procedure of system ManagedProvisioning */
	@OwnerUser public static void startDeviceOwnerPostProvisioning(final DevicePolicies policies) {
		if (! policies.isDeviceOwner()) return;
		Analytics.$().event("device_provision_manual_start").send();

		if (SDK_INT >= N) policies.clearUserRestriction(UserManager.DISALLOW_ADD_USER);
		if (SDK_INT >= N_MR1) try {
			policies.setBackupServiceEnabled(true);
		} catch (final SecurityException e) { Analytics.$().report(e); }	// "SecurityException: There should only be one user, managed by Device Owner"
	}

	/**
	 * All the preparations after the provisioning procedure of system ManagedProvisioning, also shared by manual provisioning.
	 *
	 * <p>{@link #POST_PROVISION_REV} must be increased if anything new is added in this method
	 */
	@ProfileUser private static void startProfileOwnerPostProvisioning(final Context context, final DevicePolicies policies, final @Nullable SharedPreferences prefs) {
		Log.d(TAG, "Start post-provisioning.");

		if (SDK_INT >= M) policies.addUserRestriction(UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);

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

		// Prepare critical apps
		enableCriticalAppsIfNeeded(context, new DevicePolicies(context), prefs);
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
