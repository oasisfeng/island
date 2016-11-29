package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.provider.FirebaseInitProvider;
import com.oasisfeng.android.Manifest;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.IslandDeviceAdminReceiver;
import com.oasisfeng.island.MainActivity;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.api.ApiActivity;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.UserManager.ALLOW_PARENT_PROFILE_APP_LINKING;

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
	private static final int POST_PROVISION_REV = 7;

	public static boolean isEncryptionRequired() {
		return SDK_INT < N
				&& ! Hacks.SystemProperties_getBoolean.invoke("persist.sys.no_req_encrypt", false).statically();
	}

	public static boolean isDeviceEncrypted(final Context context) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		final int status = dpm.getStorageEncryptionStatus();
		return status == ENCRYPTION_STATUS_ACTIVE // TODO: || (SDK_INT >= N && StorageManager.isEncrypted())
				|| (SDK_INT >= M && status == ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY);
	}

	/**
	 * Initiates the managed profile provisioning. If we already have a managed profile set up on
	 * this device, we will get an error dialog in the following provisioning phase.
	 */
	public static boolean provisionManagedProfile(final @NonNull Fragment fragment, final int request_code) {
		final Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
		if (SDK_INT >= M) {
			intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(fragment.getActivity(), IslandDeviceAdminReceiver.class));
			intent.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
		} else //noinspection deprecation
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, fragment.getActivity().getPackageName());
		if (intent.resolveActivity(fragment.getActivity().getPackageManager()) == null) return false;
		fragment.startActivityForResult(intent, request_code);
		return true;
	}

	private static void provisionDeviceOwner(final @NonNull Activity activity, final int request_code) {
		final Intent intent;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
					.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(activity, IslandDeviceAdminReceiver.class))
					.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
		else //noinspection deprecation
			intent = new Intent(LEGACY_ACTION_PROVISION_MANAGED_DEVICE)
					.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, activity.getPackageName());
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
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).commit();
		startProfileOwnerPostProvisioning(context, new IslandManager(context));
		disableLauncherActivity(context);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).commit();

		MainActivity.startAsUser(context, GlobalStatus.OWNER);
	}

	private static void disableLauncherActivity(final Context context) {
		setComponentEnabledSetting(context, MainActivity.class, false);		// To mark the finish of post-provisioning
	}

	/** This method always runs in managed profile */
	@SuppressLint("CommitPrefEdits") public static void startProfileOwnerProvisioningIfNeeded(final Context context) {
		final IslandManager island = new IslandManager(context);
		if (island.isDeviceOwner()) return;	// Do nothing for device owner
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
		island.resetForwarding();
		ProfileOwnerSystemProvisioning.start(island);	// Simulate the stock managed profile provision
		IslandProvisioning.startProfileOwnerPostProvisioning(context, island);
		disableLauncherActivity(context);

		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, POST_PROVISION_REV).commit();
	}

	/**
	 * All the preparations after the provisioning procedure of system ManagedProvisioning
	 *
	 * <p>{@link #POST_PROVISION_REV} must be increased if anything new is added in this method
	 */
	private static void startProfileOwnerPostProvisioning(final Context context, final IslandManager island) {
		new SystemAppsManager(context, island).prepareSystemApps();
		island.enableProfile();
		enableAdditionalForwarding(island);

		if (SDK_INT >= M) {
			final DevicePolicies dpm = new DevicePolicies(context);
			dpm.addUserRestriction(ALLOW_PARENT_PROFILE_APP_LINKING);
			dpm.setPermissionGrantState(context.getPackageName(), Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANT_STATE_GRANTED);
		}

		// Prepare AppLaunchShortcut
		setComponentEnabledSetting(context, AppLaunchShortcut.class, true);
		final IntentFilter launchpad_filter = new IntentFilter(AppLaunchShortcut.ACTION_LAUNCH_CLONE);
		launchpad_filter.addDataScheme("target");
		launchpad_filter.addCategory(Intent.CATEGORY_DEFAULT);
		launchpad_filter.addCategory(Intent.CATEGORY_LAUNCHER);
		island.enableForwarding(launchpad_filter, FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare ServiceShuttle
		setComponentEnabledSetting(context, ServiceShuttle.class, true);
		island.enableForwarding(new IntentFilter(ServiceShuttle.ACTION_BIND_SERVICE), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Prepare API
		setComponentEnabledSetting(context, ApiActivity.class, true);
		island.enableForwarding(new IntentFilter(ApiActivity.ACTION_GET_APP_LIST), FLAG_MANAGED_CAN_ACCESS_PARENT);
		island.enableForwarding(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("packages"), FLAG_MANAGED_CAN_ACCESS_PARENT);
		island.enableForwarding(IntentFilters.forAction(ApiActivity.ACTION_FREEZE).withDataScheme("package"), FLAG_MANAGED_CAN_ACCESS_PARENT);

		// Disable Firebase (to improve process initialization performance)
		setComponentEnabledSetting(context, FirebaseInitProvider.class, false);
	}

	private static void setComponentEnabledSetting(final Context context, final Class<?> clazz, final boolean enable_or_disable) {
		final int new_state = enable_or_disable ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
		context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, clazz), new_state, DONT_KILL_APP);
	}

	private static void enableAdditionalForwarding(final IslandManager island) {
		// For sharing across Island (bidirectional)
		island.enableForwarding(new IntentFilter(Intent.ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		final IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		browser.addDataScheme("http"); browser.addDataScheme("https"); browser.addDataScheme("ftp");
		island.enableForwarding(browser, FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	public static int getMaxSupportedUsers() {
		final Integer max_users = Hacks.SystemProperties_getInt.invoke("fw.max_users", - 1).statically();
		if (max_users != null && max_users != -1) {
			Analytics.$().setProperty("sys_prop.fw.max_users", max_users.toString());
			return max_users;
		}

		final Resources sys_res = Resources.getSystem();
		final int res = sys_res.getIdentifier(RES_MAX_USERS, "integer", "android");
		if (res != 0) {
			final int sys_max_users = Resources.getSystem().getInteger(res);
			Analytics.$().setProperty("sys_res." + RES_MAX_USERS, String.valueOf(sys_max_users));
			return sys_max_users;
		}
		return 1;
	}
	private static final String RES_MAX_USERS = "config_multiuserMaximumUsers";

	private IslandProvisioning() {}

	private static final String TAG = "Island.Provision";
}
