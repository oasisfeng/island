package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.provider.FirebaseInitProvider;
import com.oasisfeng.android.Manifest.permission;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.IslandDeviceAdminReceiver;
import com.oasisfeng.island.api.ApiActivity;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.IslandManagerService;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.engine.SystemAppsManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shortcut.AbstractAppLaunchShortcut;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.shuttle.ServiceShuttleActivity;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * The one-time provisioning for newly created managed profile of Island
 *
 * Created by Oasis on 2016/4/26.
 */
public class IslandProvisioning {

	/**
	 * Provision state:
	 *   1 - Managed profile provision (stock) is completed
	 *   2 - Island provision is started, POST_PROVISION_REV - Island provision is completed.
	 *   [3,POST_PROVISION_REV> - Island provision is completed in previous version, but needs re-performing in this version.
	 */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";
	/** The revision for post-provisioning. Increase this const value if post-provisioning needs to be re-performed after upgrade. */
	private static final int POST_PROVISION_REV = 8;

	private static void provisionDeviceOwner(final @NonNull Activity activity, final int request_code) {
		final Intent intent;
		if (SDK_INT < M)	//noinspection deprecation
			intent = new Intent(LEGACY_ACTION_PROVISION_MANAGED_DEVICE)
					.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, activity.getPackageName());
		else intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(activity, IslandDeviceAdminReceiver.class))
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);

		if (intent.resolveActivity(activity.getPackageManager()) != null) {
			activity.startActivityForResult(intent, request_code);
			activity.finish();
		} else Toast.makeText(activity, "Sorry, Island is not supported by your device.", Toast.LENGTH_SHORT).show();
	}
	private static final String LEGACY_ACTION_PROVISION_MANAGED_DEVICE = "com.android.managedprovisioning.ACTION_PROVISION_MANAGED_DEVICE";

	/** This is the normal procedure after ManagedProvision finished its provisioning, running in profile. */
	@SuppressLint("CommitPrefEdits") public static void onProfileProvisioningComplete(final Context context) {
		Log.d(TAG, "onProfileProvisioningComplete");
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).apply();
		startProfileOwnerPostProvisioning(context, new IslandManagerService(context), new DevicePolicies(context));
		disableLauncherActivity(context);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).apply();

		launchMainActivityAsUser(context, GlobalStatus.OWNER);
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

	/** This method always runs in managed profile */
	@SuppressLint("CommitPrefEdits") public static void startProfileOwnerProvisioningIfNeeded(final Context context) {
		final IslandManagerService island = new IslandManagerService(context);
		if (GlobalStatus.running_in_owner && new IslandManager(context).isDeviceOwner()) return;	// Do nothing for device owner
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final int state = prefs.getInt(PREF_KEY_PROVISION_STATE, 0);
		if (state >= POST_PROVISION_REV) return;	// Already provisioned (revision up to date)
		if (state == 2) {
			Log.w(TAG, "Last provision attempt failed, no more attempts...");
//			Analytics.$().event("profile_post_provision_failed").send();
			disableLauncherActivity(context);
			return;		// Last attempt failed again, no more attempts.
		} else if (state == 1) {
			Log.w(TAG, "System provisioning might be interrupted, try our own provisioning once more...");
//			Analytics.$().event("profile_provision_failed").send();
		}
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 2).commit();	// Avoid further attempts
		if (state >= 3) Log.i(TAG, "Re-performing provision for new revision " + POST_PROVISION_REV);

		// Always perform all the required provisioning steps covered by stock ManagedProvisioning, in case something is missing there.
		// This is also required for manual provision via ADB shell.
		final DevicePolicies policies = new DevicePolicies(context);
		policies.clearCrossProfileIntentFilters();
		ProfileOwnerSystemProvisioning.start(policies);	// Simulate the stock managed profile provision
		startProfileOwnerPostProvisioning(context, island, policies);
		disableLauncherActivity(context);

		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).commit();
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
		setComponentEnabledSetting(context, ServiceShuttleActivity.class, true);
		policies.addCrossProfileIntentFilter(new IntentFilter(ServiceShuttle.ACTION_BIND_SERVICE), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare API
		policies.addCrossProfileIntentFilter(new IntentFilter(ApiActivity.ACTION_GET_APP_LIST), FLAG_MANAGED_CAN_ACCESS_PARENT);
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("packages"), FLAG_MANAGED_CAN_ACCESS_PARENT);
		policies.addCrossProfileIntentFilter(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("package"), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Disable Firebase (to improve process initialization performance)
		setComponentEnabledSetting(context, FirebaseInitProvider.class, false);
	}

	private static void setComponentEnabledSetting(final Context context, final Class<?> clazz, final boolean enable_or_disable) {
		final int new_state = enable_or_disable ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
		context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, clazz), new_state, DONT_KILL_APP);
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

	private IslandProvisioning() {}

	private static final String TAG = "Island.Provision";
}
