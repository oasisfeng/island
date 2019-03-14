package com.oasisfeng.island.setup;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.databinding.ObservableInt;
import eu.chainfire.libsuperuser.Shell;
import java9.util.Optional;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_NAME;

/**
 * View model for setup fragment
 *
 * Created by Oasis on 2016/4/19.
 */
public class SetupViewModel implements Parcelable {

	private static final int REQUEST_PROVISION_MANAGED_PROFILE = 1;

	public @StringRes int message;
	public @Nullable Object[] message_params;
	int button_back;
	ObservableInt button_next = new ObservableInt();		// 0 (enabled), -1 (disabled) or custom string resource
	public int action_extra;

//	SetupViewModel(final Context context) {
//		final UserHandle profile = IslandManager.getManagedProfile(context);
//		if (profile != null) {
//			final ComponentName profile_owner = IslandManager.getProfileOwner(context, profile);
//			has_other_owner = profile_owner != null;
//			owner_name = profile_owner == null ? null : readOwnerLabel(context, profile_owner);
//		} else { has_other_owner = false; owner_name = null; }
//	}

	@Nullable SetupViewModel onNavigateNext(final Fragment fragment) {
		final Activity activity = fragment.getActivity();
		if (button_next.get() == R.string.button_setup_troubleshooting) {
			WebContent.view(activity, Config.URL_SETUP_TROUBLESHOOTING.get());
			return null;
		}
		final SetupViewModel result = checkManagedProvisioningPrerequisites(activity, mIncompleteSetupAcked);
		if (result != null) return result;

		final boolean encryption_required = isEncryptionRequired();
		Analytics.$().setProperty(Analytics.Property.EncryptionRequired, encryption_required);
		if (encryption_required) {
			final boolean device_encrypted = isDeviceEncrypted(activity);
			Analytics.$().setProperty(Analytics.Property.DeviceEncrypted, device_encrypted);
			if (! device_encrypted) {
				if (message == R.string.dialog_encryption_required) {	// "Next" is clicked in the "Encryption Required" step.
					SafeAsyncTask.execute(activity, _a -> {
						try {		// Worker thread
							Shell.SU.run("setprop persist.sys.no_req_encrypt 1");
						} catch (final Exception e) {
							Log.e(TAG, "Error running root command", e);
						}
						return null;
					}, _r -> {		// Main thread
						if (! isEncryptionRequired()) Analytics.$().event("encryption_skipped").send();
						if (fragment.getActivity() == null) return;
						startManagedProvisioning(fragment);
					});
					return null;
				}
				final SetupViewModel vm = buildErrorVM(R.string.dialog_encryption_required, null);
				vm.button_next.set(0);		// Enable the "next" button.
				return vm;
			}
		}

		startManagedProvisioning(fragment);
		return null;
	}

	/** @return null if all prerequisites met TODO: Move this method to IslandSetup */
	public static @CheckResult SetupViewModel checkManagedProvisioningPrerequisites(final Context context, final boolean ignore_incomplete_setup) {
		final PackageManager pm = context.getPackageManager();
		if (buildManagedProfileProvisioningIntent(context).resolveActivity(pm) == null)
			return buildErrorVM(R.string.setup_error_missing_managed_provisioning, reason("lack_managed_provisioning"));

		// Check for incomplete provisioning, before DPM.isProvisioningAllowed() check which returns true in this case.
		Optional<ComponentName> owner;
		if (SDK_INT >= N && Users.profile == null) for (final int profile_id : IslandManager.getProfileIdsIncludingDisabled(context)) {
			if (Users.isOwner(profile_id) || (owner = DevicePolicies.getProfileOwnerAsUser(context, profile_id)) == null || ! owner.isPresent())
				continue;
			final ComponentName profile_owner = owner.get();
			if (! Modules.MODULE_ENGINE.equals(profile_owner.getPackageName())) {
				final CharSequence label = readOwnerLabel(context, profile_owner);
				reason("existent_work_profile").with(ITEM_ID, profile_owner.getPackageName()).with(ITEM_NAME, label != null ? label.toString() : null).send();
				continue;
			}
			if (ignore_incomplete_setup) continue;
			return buildErrorVM(R.string.setup_error_provisioning_incomplete, reason("provisioning_incomplete")).withExtraAction(R.string.button_have_checked);
		}

		// DPM.isProvisioningAllowed() is the one-stop prerequisites checking.
		if (SDK_INT >= N) {
			final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
			if (dpm != null && dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE))
				Analytics.$().event("device_provision_allowed").send();	// Special analytics
			if (dpm != null && dpm.isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE)) return null;
		}

		final boolean has_managed_users = pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
		if (! has_managed_users)
			return buildErrorVM(R.string.setup_error_managed_profile_not_supported, reason("lack_managed_users"));

		if (SDK_INT < M) {		// The max-users limitation is no longer enforced since Android M.
			final Integer sys_prop_max_users = IslandSetup.getSysPropMaxUsers();
			if (sys_prop_max_users == null || sys_prop_max_users == -1) {
				final Integer res_config_max_users = IslandSetup.getResConfigMaxUsers();
				if (res_config_max_users == null || res_config_max_users < 2)
					return buildErrorVM(R.string.setup_error_multi_user_not_allowed, reason(IslandSetup.RES_MAX_USERS).with(ITEM_ID, String.valueOf(res_config_max_users)));
			} else if (sys_prop_max_users < 2) return buildErrorVM(R.string.setup_error_multi_user_not_allowed, reason("fw.max_users"));
		}

		if (SDK_INT >= M) {
			final String device_owner = new DevicePolicies(context).getDeviceOwner();
			if (device_owner != null) {
				CharSequence owner_label = null;
				try {
					owner_label = pm.getApplicationInfo(device_owner, PackageManager.GET_UNINSTALLED_PACKAGES).loadLabel(pm);
				} catch (final PackageManager.NameNotFoundException ignored) {}		// Should never happen.

				final SetupViewModel error = buildErrorVM(R.string.setup_error_managed_device, reason("managed_device").with(ITEM_ID, device_owner));
				error.message_params = new String[] { owner_label != null ? owner_label.toString() : device_owner };
				error.action_extra = 0;		// Disable the manual-setup prompt, because device owner cannot be removed by 3rd-party.
				return error;
			}
		}

		if (SDK_INT >= N) reason("disallowed").send();		// Disallowed by DPC for unknown reason, just log this but let user have a try.
		return null;
	}

	private static Analytics.Event reason(final String reason) {
		return Analytics.$().event("setup_island_failure").with(Analytics.Param.ITEM_CATEGORY, reason);
	}

	private static boolean isEncryptionRequired() {
		return SDK_INT < N && ! Hacks.SystemProperties_getBoolean.invoke("persist.sys.no_req_encrypt", false).statically();
	}

	private static boolean isDeviceEncrypted(final Context context) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		if (dpm == null) return false;
		final int status = dpm.getStorageEncryptionStatus();
		return status == ENCRYPTION_STATUS_ACTIVE // TODO: || (SDK_INT >= N && StorageManager.isEncrypted())
				|| (SDK_INT >= M && status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY);
	}

	public void onExtraButtonClick(final View view) {
		final Context context = view.getContext();
		if (action_extra == R.string.button_instructions_online) {
			WebContent.view(context, Config.URL_SETUP.get());
		} else if (action_extra == R.string.button_account_settings) {
			Activities.startActivity(context, new Intent(Settings.ACTION_SYNC_SETTINGS));
			if (context instanceof Activity) ((Activity) context).finish();
		} else if (action_extra == R.string.button_have_checked) {
			button_next.set(0);
			mIncompleteSetupAcked = true;
			view.setVisibility(View.GONE);
		} else if (action_extra == R.string.button_setup_island_with_root) {
			IslandSetup.requestProfileOwnerSetupWithRoot(Activities.findActivityFrom(context));
		} else throw new IllegalStateException();
	}

	static SetupViewModel onActivityResult(final Activity activity, final int request, final int result) {
		// Activity result of managed provisioning is only delivered since Android 6.
		if (request != REQUEST_PROVISION_MANAGED_PROFILE) return null;
		if (result == Activity.RESULT_CANCELED) {
			Log.i(TAG, "Provision is cancelled.");
			Analytics.$().event("profile_provision_sys_activity_canceled").send();
			return buildErrorVM(R.string.setup_solution_for_cancelled_provision, reason("provisioning_cancelled"))
					.withExtraAction(R.string.button_setup_island_with_root).withNextButton(R.string.button_setup_troubleshooting);
		}
		if (result == Activity.RESULT_OK) {
			Log.i(TAG, "System provision activity is done.");
			Analytics.$().event("profile_provision_sys_activity_done").send();
			Toast.makeText(activity, R.string.toast_setup_in_progress, Toast.LENGTH_LONG).show();
			activity.finish();
		}
		return null;
	}

	private static void startManagedProvisioning(final Fragment fragment) {
		final Activity activity = fragment.getActivity();
		final Intent intent = buildManagedProfileProvisioningIntent(activity);
		try {
			fragment.startActivityForResult(intent, REQUEST_PROVISION_MANAGED_PROFILE);
			Analytics.$().event("profile_provision_sys_activity_start").send();
			if (SDK_INT < M) activity.finish();			// No activity result on Android 5.x, thus we have to finish the activity now.
		} catch (final IllegalStateException e) {	// Fragment not in proper state
			activity.startActivity(intent);				// Fall-back to starting activity without result observation.
			activity.finish();
		} catch (final ActivityNotFoundException ignored) {}	// Should not happen since it was checked beforehand
	}

	private static SetupViewModel buildErrorVM(final @StringRes int message, final @Nullable Analytics.Event event) {
		if (event != null) event.send();
		final SetupViewModel next = new SetupViewModel();
		next.message = message;
		next.button_next.set(-1);
		next.action_extra = R.string.button_instructions_online;	// Default extra action, can be overridden by withExtraAction().
		return next;
	}

	private SetupViewModel withExtraAction(final @StringRes int text) { action_extra = text; return this; }

	private SetupViewModel withNextButton(final @StringRes int label) { button_next.set(label); return this; }

	private static Intent buildManagedProfileProvisioningIntent(final Context context) {
		final Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
		if (SDK_INT >= M) {
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, DeviceAdmins.getComponentName(context));
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
		} else //noinspection deprecation
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, Modules.MODULE_ENGINE);
		if (BuildConfig.DEBUG && SDK_INT >= M)
			intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, new Account("default_account", "miui_yellowpage"));
		if (SDK_INT >= O) intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, true);
		return intent;
	}

	/** Initiates the managed device provisioning */
	@RequiresApi(M) private static Intent buildManagedDeviceProvisioningIntent(final @NonNull Fragment fragment, final int request_code) {
		return new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, DeviceAdmins.getComponentName(fragment.getContext()))
				.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);		// Actually works on Android 7+.
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
		button_next.set(in.readInt());
		action_extra = in.readInt();
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(message);
		dest.writeInt(button_back);
		dest.writeInt(button_next.get());
		dest.writeInt(action_extra);
	}

	@Override public int describeContents() { return 0; }

	public static final Creator<SetupViewModel> CREATOR = new Creator<SetupViewModel>() {
		@Override public SetupViewModel createFromParcel(final Parcel in) { return new SetupViewModel(in); }
		@Override public SetupViewModel[] newArray(final int size) { return new SetupViewModel[size]; }
	};

	private boolean mIncompleteSetupAcked;

	private static final String TAG = "Island.Setup";
}
