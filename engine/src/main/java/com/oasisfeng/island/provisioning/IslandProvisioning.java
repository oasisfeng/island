package com.oasisfeng.island.provisioning;

import android.app.IntentService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.oasisfeng.android.Manifest.permission;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.api.ApiActivity;
import com.oasisfeng.island.engine.IslandManagerService;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.shortcut.AbstractAppLaunchShortcut;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;

import java.util.Set;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * The one-time provisioning for newly created managed profile of Island
 *
 * Created by Oasis on 2016/4/26.
 */
public class IslandProvisioning extends IntentService {

	/**
	 * Provision state:
	 *   1 - Managed profile provision (stock) is completed
	 *   2 - Island provision is started, POST_PROVISION_REV - Island provision is completed.
	 *   [3,POST_PROVISION_REV> - Island provision is completed in previous version, but needs re-performing in this version.
	 *   POST_PROVISION_REV - Island provision is up-to-date, nothing to do.
	 */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";
	/** The revision for post-provisioning. Increase this const value if post-provisioning needs to be re-performed after upgrade. */
	private static final int POST_PROVISION_REV = 8;

	private static final String PREF_KEY_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION = "sys.apps.rev";
	/** The revision for critical system packages list. Increase this const value if the list of critical system packages are changed after upgrade. */
	private static final int UP_TO_DATE_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION = 1;

	/** This is the normal procedure after ManagedProvision finished its provisioning, running in profile. */
	public static void onProfileProvisioningComplete(final Context context) {
		Log.d(TAG, "onProfileProvisioningComplete");
		if (Users.isOwner()) return;		// Nothing to do for managed device provisioning.
		context.startService(new Intent(context, IslandProvisioning.class));
	}

	@Override protected void onHandleIntent(@Nullable final Intent intent) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).apply();
		startProfileOwnerPostProvisioning(this, new IslandManagerService(this), new DevicePolicies(this));
		disableLauncherActivity(this);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).apply();

		for (final UserHandle user : ((UserManager) getSystemService(Context.USER_SERVICE)).getUserProfiles())
			if (Users.isOwner(user)) {			// To get the UserHandle of owner user.
				if (! launchMainActivityAsUser(this, user))
					Log.e(TAG, "Failed to launch main activity in owner user.");
				break;
			}
	}

	private static boolean launchMainActivityAsUser(final Context context, final UserHandle user) {
		final LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		final ComponentName activity = Modules.getMainLaunchActivity(context);
		if (! apps.isActivityEnabled(activity, user)) return false;
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

	public static void enableCriticalSystemAppsIfNeeded(final Context context, final IslandManagerService island, final @Nullable SharedPreferences prefs) {
		if (prefs != null && checkRevision(prefs, PREF_KEY_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION, UP_TO_DATE_CRITICAL_SYSTEM_PACKAGE_LIST_REVISION) == -1) return;
		@SuppressWarnings("deprecation") final Set<String> pkgs
				= SystemAppsManager.detectCriticalSystemPackages(context.getPackageManager(), island, PackageManager.GET_UNINSTALLED_PACKAGES);
		for (final String pkg : pkgs) try {
			island.enableSystemApp(pkg);        // FIXME: Don't re-enable explicitly cloned system apps. (see ClonedHiddenSystemApps)
			island.unfreezeApp(pkg);
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

	@ProfileUser public static void startProfileOwnerProvisioningIfNeeded(final Context context, final @Nullable SharedPreferences prefs) {
		if (Users.isOwner()) return;	// Do nothing in owner user
		final IslandManagerService island = new IslandManagerService(context);
		if (prefs != null) {
			final int state = checkRevision(prefs, PREF_KEY_PROVISION_STATE, POST_PROVISION_REV);
			if (state == -1) return;		// Already provisioned (revision up to date)
			if (state == 2) {
				Log.w(TAG, "Last provision attempt failed, no more attempts...");
//			Analytics.$().event("profile_post_provision_failed").send();
				disableLauncherActivity(context);
				return;		// Last attempt failed again, no more attempts.
			} else if (state == 1) {
				Log.w(TAG, "System provisioning might be interrupted, try our own provisioning once more...");
//			Analytics.$().event("profile_provision_failed").send();
			} else if (state >= 3) Log.i(TAG, "Re-performing provision for new revision " + POST_PROVISION_REV);
			prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 2).apply();		// Avoid further attempts
		}

		// Always perform all the required provisioning steps covered by stock ManagedProvisioning, in case something is missing there.
		// This is also required for manual provision via ADB shell.
		final DevicePolicies policies = new DevicePolicies(context);
		policies.clearCrossProfileIntentFilters();

		ProfileOwnerSystemProvisioning.start(policies);	// Simulate the stock managed profile provision

		startProfileOwnerPostProvisioning(context, island, policies);

		disableLauncherActivity(context);

		if (prefs != null) prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).apply();
	}

	/**
	 * All the preparations after the provisioning procedure of system ManagedProvisioning
	 *
	 * <p>{@link #POST_PROVISION_REV} must be increased if anything new is added in this method
	 */
	private static void startProfileOwnerPostProvisioning(final Context context, final IslandManagerService island, final DevicePolicies policies) {
		new SystemAppsManager(context, island).prepareSystemApps();
		Log.d(TAG, "Enable profile now.");
		policies.setProfileName(context.getString(R.string.profile_name));
		// Enable the profile here, launcher will show all apps inside.
		policies.setProfileEnabled();

		enableAdditionalForwarding(policies);

		if (SDK_INT >= M) {
			final DevicePolicies dpm = new DevicePolicies(context);
			dpm.addUserRestriction(UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);
			dpm.setPermissionGrantState(context.getPackageName(), permission.INTERACT_ACROSS_USERS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
		}

		// Prepare AppLaunchShortcut
		final IntentFilter launchpad_filter = new IntentFilter(AbstractAppLaunchShortcut.ACTION_LAUNCH_CLONE);
		launchpad_filter.addDataScheme("target");
		launchpad_filter.addCategory(Intent.CATEGORY_DEFAULT);
		launchpad_filter.addCategory(Intent.CATEGORY_LAUNCHER);
		policies.addCrossProfileIntentFilter(launchpad_filter, FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare ServiceShuttle
		policies.addCrossProfileIntentFilter(new IntentFilter(ServiceShuttle.ACTION_BIND_SERVICE), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare API
		policies.addCrossProfileIntentFilter(new IntentFilter(ApiActivity.ACTION_GET_APP_LIST), FLAG_MANAGED_CAN_ACCESS_PARENT);
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("packages"), FLAG_MANAGED_CAN_ACCESS_PARENT);
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("package"), FLAG_MANAGED_CAN_ACCESS_PARENT);
	}

	private static void enableAdditionalForwarding(final DevicePolicies policies) {
		// For sharing across Island (bidirectional)
		policies.addCrossProfileIntentFilter(new IntentFilter(Intent.ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		final IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		browser.addDataScheme("http"); browser.addDataScheme("https"); browser.addDataScheme("ftp");
		policies.addCrossProfileIntentFilter(browser, FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	public IslandProvisioning() { super("Provisioning"); }

	private static final String TAG = "Island.Provision";
}
