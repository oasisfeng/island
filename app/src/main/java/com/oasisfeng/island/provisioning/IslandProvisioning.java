package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.island.IslandDeviceAdminReceiver;
import com.oasisfeng.island.MainActivity;
import com.oasisfeng.island.R;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
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

	/** Provision state: 1 - Managed profile provision is completed, 2 - Island provision is started, 3 - Island provision is completed */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";

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
	public static boolean provisionManagedProfile(final @NonNull Activity activity, final int request_code) {
		final Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
		if (SDK_INT >= M) {
			intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(activity, IslandDeviceAdminReceiver.class));
			intent.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
		} else //noinspection deprecation
			intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, activity.getPackageName());
		if (intent.resolveActivity(activity.getPackageManager()) == null) return false;
		activity.startActivityForResult(intent, request_code);
//		activity.finish();
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

	/** This method always runs in managed profile */
	public static void finishIncompleteProvisioning(final Activity activity) {
		if (! new IslandManager(activity).isProfileOwnerActive()) {
			Analytics.$().event("inactive_device_admin").send();
			activity.startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
					.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, IslandDeviceAdminReceiver.getComponentName(activity))
					.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.dialog_reactivate_message)));
			// TODO: Check result
			return;
		}
		startProfileOwnerProvisioningIfNeeded(activity);
	}

	@SuppressLint("CommitPrefEdits") public void onProfileProvisioningComplete() {
		Log.d(TAG, "onProfileProvisioningComplete");
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).commit();
		startProfileOwnerPostProvisioning();
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();

		MainActivity.startAsUser(mContext, GlobalStatus.OWNER);
	}

	@SuppressLint("CommitPrefEdits") public static void startProfileOwnerProvisioningIfNeeded(final Context context) {
		if (IslandManager.isDeviceOwner(context)) return;	// Do nothing for device owner
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final int state = prefs.getInt(PREF_KEY_PROVISION_STATE, 0);
		if (state >= 3) return;		// Already provisioned
		if (state == 2) {
			Log.w(TAG, "Last provision attempt failed, no more attempts...");
			return;		// Last attempt failed again, no more attempts.
		} else if (state == 1) Log.w(TAG, "Last provision attempt might be interrupted, try provisioning one more time...");
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 2).commit();	// Avoid further attempts

		if (state == 0)		// Managed profile provision was not performed, the profile may be enabled manually.
			ProfileOwnerSystemProvisioning.start(new IslandManager(context));	// Simulate the stock managed profile provision

		new IslandProvisioning(context).startProfileOwnerPostProvisioning();	// Last provision attempt may be interrupted

		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();
	}

	/** All the preparations after the provisioning procedure of system ManagedProvisioning */
	private void startProfileOwnerPostProvisioning() {
		new SystemAppsManager(mContext, mIslandManager).prepareSystemApps();
		mIslandManager.enableProfile();
		enableAdditionalForwarding();
		if (SDK_INT >= M) {
			final DevicePolicies dpm = new DevicePolicies(mContext);
			dpm.addUserRestriction(ALLOW_PARENT_PROFILE_APP_LINKING);
			dpm.setPermissionGrantState(mContext.getPackageName(), "android.permission.INTERACT_ACROSS_USERS", PERMISSION_GRANT_STATE_GRANTED);
		}

		final PackageManager pm = mContext.getPackageManager();
		// Prepare ServiceShuttle
		pm.setComponentEnabledSetting(new ComponentName(mContext, ServiceShuttle.class), COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
		mIslandManager.enableForwarding(new IntentFilter(ServiceShuttle.ACTION_BIND_SERVICE),
				FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// Disable the launcher entry inside profile, to mark the finish of post-provisioning.
		mContext.getPackageManager().setComponentEnabledSetting(new ComponentName(mContext, MainActivity.class), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
	}

	private void enableAdditionalForwarding() {
		// For sharing across Island (bidirectional)
		mIslandManager.enableForwarding(new IntentFilter(Intent.ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		final IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		browser.addDataScheme("http"); browser.addDataScheme("https"); browser.addDataScheme("ftp");
		mIslandManager.enableForwarding(browser, FLAG_PARENT_CAN_ACCESS_MANAGED);
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

	public IslandProvisioning(final Context context) {
		mContext = context;
		mIslandManager = new IslandManager(context);
	}

	private final Context mContext;
	private final IslandManager mIslandManager;

	private static final String TAG = "Island.Provision";
}
