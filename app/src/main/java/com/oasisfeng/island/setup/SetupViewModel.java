package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;

import eu.chainfire.libsuperuser.Shell;
import java8.util.Optional;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * View model for setup fragment
 *
 * Created by Oasis on 2016/4/19.
 */
public class SetupViewModel implements Parcelable {

	private static final String RES_MAX_USERS = "config_multiuserMaximumUsers";

	private static final int REQUEST_PROVISION_MANAGED_PROFILE = 1;

	public @StringRes int message;
	int button_back;
	int button_next;
	public int button_extra;

//	SetupViewModel(final Context context) {
//		final UserHandle profile = IslandManager.getManagedProfile(context);
//		if (profile != null) {
//			final ComponentName profile_owner = IslandManager.getProfileOwner(context, profile);
//			has_other_owner = profile_owner != null;
//			owner_name = profile_owner == null ? null : readOwnerLabel(context, profile_owner);
//		} else { has_other_owner = false; owner_name = null; }
//	}

	SetupViewModel onNavigateNext(final Fragment fragment) {
		final Activity activity = fragment.getActivity();
		if (SDK_INT >= N && activity.getSystemService(DevicePolicyManager.class).isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE))
			return startManagedProvisioning(fragment);	// DevicePolicyManager.isProvisioningAllow() provides one-stop prerequisites check.

		// FIXME: Analytics.$().event("setup_lack_managed_users").send();
		final boolean has_managed_users = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
		if (! has_managed_users)
			return buildErrorVM(R.string.setup_error_managed_profile_not_supported, reason("lack_managed_users"));

		// Check for incomplete provisioning.
		if (SDK_INT >= N && DevicePolicies.getManagedProfile(activity) == null) {
			final int profile_id = IslandManager.getManagedProfileIdIncludingDisabled(activity);
			if (profile_id != 0) {
				final Optional<ComponentName> profile_owner_result = DevicePolicies.getProfileOwnerAsUser(activity, profile_id);
				if (profile_owner_result != null && profile_owner_result.isPresent()) {
					final ComponentName profile_owner = profile_owner_result.get();
					if (! activity.getPackageName().equals(profile_owner.getPackageName())) {
						final CharSequence owner_label = readOwnerLabel(activity, profile_owner);
						final Analytics.Event reason = reason("existent_work_profile").with(Analytics.Param.ITEM_ID, profile_owner.getPackageName());
						return buildErrorVM(R.string.setup_error_other_work_profile, R.string.button_account_settings,
								owner_label == null ? reason : reason.with(Analytics.Param.ITEM_NAME, owner_label.toString()));
					} else return buildErrorVM(R.string.setup_error_provisioning_incomplete, R.string.button_account_settings,
							reason("provisioning_incomplete"));
				}
			}
		}

		if (SDK_INT < M && getMaxSupportedUsers() < 2)
			return buildErrorVM(R.string.setup_error_multi_user_not_allowed, reason("sys_prop.fw.max_users"));

		// TODO Analytics.$().event("profile_owner_existent").with("package", profile_owner.getPackageName()).with("label", owner_label).send();

		final boolean encryption_required = isEncryptionRequired();
		if (Analytics.$().setProperty("encryption_required", encryption_required)
				&& ! Analytics.$().setProperty("encrypted_already", isDeviceEncrypted(activity))) {
			if (message == R.string.dialog_encryption_required) {        // Next is clicked in this step
				new AsyncTask<Void, Void, Void>() {
					@Override protected Void doInBackground(final Void... params) {
						try {
							Shell.SU.run("setprop persist.sys.no_req_encrypt 1");
						} catch (final Exception e) {
							Log.e(TAG, "Error running root command", e);
						}
						return null;
					}

					@Override protected void onPostExecute(final Void ignored) {
						if (encryption_required && ! isEncryptionRequired()) Analytics.$().event("encryption_skipped").send();
						if (fragment.getActivity() == null) return;
						startManagedProvisioning(fragment);
					}
				}.execute();
				return null;
			}
			final SetupViewModel vm = buildErrorVM(R.string.dialog_encryption_required, reason("encryption_required"));
			vm.button_next = 0;		// Enable the "next" button.
			return vm;
		}

		return startManagedProvisioning(fragment);
	}

	private static Analytics.Event reason(final String reason) {
		return Analytics.$().event("setup_island_failure").with(Analytics.Param.ITEM_CATEGORY, reason);
	}

	private static boolean isEncryptionRequired() {
		return SDK_INT < N && ! Hacks.SystemProperties_getBoolean.invoke("persist.sys.no_req_encrypt", false).statically();
	}

	private static boolean isDeviceEncrypted(final Context context) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		final int status = dpm.getStorageEncryptionStatus();
		return status == ENCRYPTION_STATUS_ACTIVE // TODO: || (SDK_INT >= N && StorageManager.isEncrypted())
				|| (SDK_INT >= M && status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY);
	}

	public void onExtraButtonClick(final Context context) {
		if (button_extra == R.string.button_instructions_online)
			WebContent.view(context, Config.URL_SETUP.get());
		else if (button_extra == R.string.button_account_settings)
			Activities.startActivity(context, new Intent(Settings.ACTION_SYNC_SETTINGS));
		else throw new IllegalStateException();
		if (context instanceof Activity) ((Activity) context).finish();
	}

	static void onActivityResult(final Activity activity, final int request, final int result) {
		// Activity result of managed provisioning is only delivered since Android 6.
		if (request == REQUEST_PROVISION_MANAGED_PROFILE) {
			if (result == Activity.RESULT_OK) {
				Log.i(TAG, "System provision activity is done.");
				Analytics.$().event("profile_provision_sys_activity_done").send();
				Toast.makeText(activity, R.string.toast_setup_completed_and_wait, Toast.LENGTH_LONG).show();
				activity.finish();
			} else if (result == Activity.RESULT_CANCELED) {
				Log.i(TAG, "Provision is cancelled.");
				Analytics.$().event("profile_provision_sys_activity_canceled").send();
			}
		}
	}

	private static SetupViewModel startManagedProvisioning(final Fragment fragment) {
		final Activity activity = fragment.getActivity();
		final Intent intent = buildManagedProfileProvisioningIntent(activity);
		try {
			fragment.startActivityForResult(intent, REQUEST_PROVISION_MANAGED_PROFILE);
			Analytics.$().event("profile_provision_sys_activity_start").send();
			if (SDK_INT < M) activity.finish();			// No activity result on Android 5.x, thus we have to finish the activity now.
		} catch (final IllegalStateException e) {	// Fragment not in proper state
			activity.startActivity(intent);				// Fall-back to starting activity without result observation.
			activity.finish();
		} catch (final ActivityNotFoundException e) {
			return buildErrorVM(R.string.error_reason_missing_managed_provisioning, reason("lack_managed_provisioning"));
		}
		return null;
	}

	private static SetupViewModel buildErrorVM(final @StringRes int message, final Analytics.Event event) {
		return buildErrorVM(message, R.string.button_instructions_online, event);
	}

	private static SetupViewModel buildErrorVM(final @StringRes int message, final @StringRes int button_extra, final Analytics.Event event) {
		event.send();
		final SetupViewModel next = new SetupViewModel();
		next.message = message;
		next.button_next = -1;
		next.button_extra = button_extra;
		return next;
	}

	private static Intent buildManagedProfileProvisioningIntent(final Context context) {
		final Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
		if (SDK_INT >= M) {
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, DeviceAdmins.getComponentName(context));
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
		} else //noinspection deprecation
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, Modules.MODULE_ENGINE);
		return intent;
	}

	/** Initiates the managed device provisioning */
	@RequiresApi(M) private static Intent buildManagedDeviceProvisioningIntent(final @NonNull Fragment fragment, final int request_code) {
		return new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, DeviceAdmins.getComponentName(fragment.getContext()))
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
	}

	private static int getMaxSupportedUsers() {
		final Integer max_users = Hacks.SystemProperties_getInt.invoke("fw.max_users", - 1).statically();
		if (max_users != null && max_users != -1) {
			Analytics.$().setProperty("sys_prop.fw.max_users", max_users.toString());
			return max_users;
		}

		final Resources sys_res = Resources.getSystem();
		final int res = sys_res.getIdentifier(RES_MAX_USERS, "integer", "android");
		if (res != 0) {
			final int sys_max_users = Resources.getSystem().getInteger(res);
			Analytics.$().setProperty("sys_res.config_maxUsers", String.valueOf(sys_max_users));
			return sys_max_users;
		}
		return 1;
	}

	private static CharSequence readOwnerLabel(final Context context, final ComponentName owner) {
		final PackageManager pm = context.getPackageManager();
		try {
			final ActivityInfo owner_info = pm.getReceiverInfo(owner, 0);	// It should be a BroadcastReceiver
			if (owner_info != null) return owner_info.loadLabel(pm);
			return pm.getApplicationInfo(owner.getPackageName(), 0).loadLabel(pm);	// If not, use app label
		} catch (final PackageManager.NameNotFoundException ignored) {
			return null;
		}
	}

	SetupViewModel() {}

	private SetupViewModel(final Parcel in) {
		message = in.readInt();
		button_back = in.readInt();
		button_next = in.readInt();
		button_extra = in.readInt();
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(message);
		dest.writeInt(button_back);
		dest.writeInt(button_next);
		dest.writeInt(button_extra);
	}

	@Override public int describeContents() { return 0; }

	public static final Creator<SetupViewModel> CREATOR = new Creator<SetupViewModel>() {
		@Override public SetupViewModel createFromParcel(final Parcel in) { return new SetupViewModel(in); }
		@Override public SetupViewModel[] newArray(final int size) { return new SetupViewModel[size]; }
	};

	private static final String TAG = "Island.Setup";
}
