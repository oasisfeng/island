package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.R;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.provisioning.IslandProvisioning;

import eu.chainfire.libsuperuser.Shell;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * View model for setup fragment
 *
 * Created by Oasis on 2016/4/19.
 */
public class SetupViewModel implements Parcelable {

	private static final int REQUEST_PROVISION_MANAGED_PROFILE = 1;

	public String message;
	int button_back;
	int button_next;
	public int button_extra;
	boolean require_scroll_to_bottom;

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
		if (SDK_INT >= N && ! activity.getSystemService(DevicePolicyManager.class).isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE))
			return buildErrorVM(activity, R.string.error_reason_provisioning_not_allowed);

		if (! activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS))
			return buildErrorVM(activity, R.string.error_reason_managed_profile_not_supported);

		if (((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice())
			return buildErrorVM(activity, R.string.error_reason_low_ram);

		if (SDK_INT < M) {
			final int max_users = IslandProvisioning.getMaxSupportedUsers();
			if (max_users < 2) return buildErrorVM(activity, R.string.error_reason_multi_user_not_allowed);
		}

		// TODO Analytics.$().event("profile_owner_existent").with("package", profile_owner.getPackageName()).with("label", owner_label).send();

		final boolean is_encryption_required = IslandProvisioning.isEncryptionRequired();
		if (Analytics.$().setProperty("encryption_required", is_encryption_required)
				&& ! Analytics.$().setProperty("encrypted_already", IslandProvisioning.isDeviceEncrypted(activity))) {
			if (this.button_extra == R.string.button_instructions_online) {		// Next is clicked in this step
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
						if (is_encryption_required && ! IslandProvisioning.isEncryptionRequired())
							Analytics.$().event("encryption_skipped").send();
						setup(fragment);
					}
				}.execute();
				return null;
			}
			final SetupViewModel next = new SetupViewModel();
			next.message = activity.getString(R.string.dialog_encryption_required);
			next.button_extra = R.string.button_instructions_online;
			next.require_scroll_to_bottom = true;
			return next;
		}

		if (! setup(fragment))
			return buildErrorVM(activity, R.string.error_reason_managed_profile_not_supported);

		return null;
	}

	public static void onExtraButtonClick(final View v) {
		WebContent.view(v.getContext(), Config.URL_PREREQUISITES.get());
	}

	static void onProvisioningFinished(final Context context) {
		Log.i(TAG, "2nd stage of provision is done.");
		GlobalStatus.profile = IslandManager.getManagedProfile(context);
	}

	static void onActivityResult(final Activity activity, final int request, final int result) {
		// Activity result of managed provisioning is only delivered since Android 6.
		if (request == REQUEST_PROVISION_MANAGED_PROFILE) {
			if (result == Activity.RESULT_OK) {
				Log.i(TAG, "1st stage of provision is done.");
				Analytics.$().event("profile_provision_1st_stage_done").send();
				Toast.makeText(activity, R.string.toast_setup_completed_and_wait, Toast.LENGTH_LONG).show();
				activity.finish();
			} else if (result == Activity.RESULT_CANCELED) {
				Log.i(TAG, "Provision is cancelled.");
				Analytics.$().event("profile_provision_cancelled").send();
			}
		}
	}

	private static boolean setup(final Fragment fragment) {
		final Activity activity = fragment.getActivity();
		final boolean result = IslandProvisioning.provisionManagedProfile(fragment, REQUEST_PROVISION_MANAGED_PROFILE);
		if (result && SDK_INT < M) activity.finish();		// No activity result on Android 5.x, thus we have to finish the activity now.
		return result;
	}

	private static SetupViewModel buildErrorVM(final Context context, final @StringRes int reason) {
		final SetupViewModel next = new SetupViewModel();
		next.message = context.getString(R.string.error_rom_incompatible, context.getString(reason));
		next.button_next = -1;
		next.button_extra = R.string.button_learn_more_online;
		return next;
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
		message = in.readString();
		button_back = in.readInt();
		button_next = in.readInt();
		button_extra = in.readInt();
		require_scroll_to_bottom = in.readByte() != 0;
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeString(message);
		dest.writeInt(button_back);
		dest.writeInt(button_next);
		dest.writeInt(button_extra);
		dest.writeByte((byte) (require_scroll_to_bottom ? 1 : 0));
	}

	@Override public int describeContents() { return 0; }

	public static final Creator<SetupViewModel> CREATOR = new Creator<SetupViewModel>() {
		@Override public SetupViewModel createFromParcel(final Parcel in) { return new SetupViewModel(in); }
		@Override public SetupViewModel[] newArray(final int size) { return new SetupViewModel[size]; }
	};

	private static final String TAG = "Island.Setup";
}
