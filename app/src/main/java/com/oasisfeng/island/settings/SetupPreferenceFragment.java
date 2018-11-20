package com.oasisfeng.island.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.android.widget.Toasts;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.setup.IslandSetup;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.setup.SetupViewModel;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.settings.ActionButtonPreference;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import java9.util.Optional;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Settings - Setup
 *
 * Created by Oasis on 2017/3/9.
 */
public class SetupPreferenceFragment extends SettingsActivity.SubPreferenceFragment {

	private static final int REQUEST_DEVICE_OWNER_ACTIVATION = 1;

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Activity activity = getActivity();

		final ActionButtonPreference pref_setup_mainland = (ActionButtonPreference) findPreference(getString(R.string.key_setup_mainland));
		final Preference pref_mainland_reprovisioning = findPreference(getString(R.string.key_setup_mainland_reprovision));
		if (new DevicePolicies(activity).isActiveDeviceOwner()) {
			pref_setup_mainland.setSummaryAndActionButton(R.string.pref_setup_mainland_summary_managed, R.drawable.ic_cancel_black_24dp, p -> {
				IslandSetup.requestDeviceOwnerDeactivation(getActivity());
				return true;
			});

			pref_mainland_reprovisioning.setEnabled(true);
			pref_mainland_reprovisioning.setOnPreferenceClickListener(p -> {
				final Context context = getActivity();		// ACTION_PROVISION_MANAGED_DEVICE is handled by IslandProvisioning.
				if (context != null && null == context.startService(new Intent(ACTION_PROVISION_MANAGED_DEVICE).setPackage(Modules.MODULE_ENGINE)))
					Toast.makeText(activity, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
				return true;
			});
		} else {
			pref_setup_mainland.setSummaryAndActionButton(R.string.pref_setup_mainland_summary_not_managed, R.drawable.ic_build_black_24dp, p -> {
				IslandSetup.requestDeviceOwnerActivation(this, REQUEST_DEVICE_OWNER_ACTIVATION);
				return true;
			});
			removeLeafPreference(getPreferenceScreen(), pref_mainland_reprovisioning);
		}

		final ActionButtonPreference pref_island = (ActionButtonPreference) findPreference(getString(R.string.key_setup_island));
		final int disabled_profile;
		if (Users.profile != null) {
			final Optional<Boolean> is_enabled_profile_owner = DevicePolicies.isOwnerOfEnabledProfile(activity);
			if (is_enabled_profile_owner == null || ! is_enabled_profile_owner.isPresent()) {
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_unknown, R.drawable.ic_delete_forever_black_24dp, p -> startAccountSettingActivity());
			} else if (is_enabled_profile_owner.get()) {    // Normal (managed by Island)
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed, R.drawable.ic_delete_forever_black_24dp, p -> {
					IslandSetup.requestProfileRemoval(getActivity());
					return true;
				});
				final Preference pref_island_reprovisioning = findPreference(getString(R.string.key_setup_island_reprovision));
				pref_island_reprovisioning.setEnabled(true);
				pref_island_reprovisioning.setOnPreferenceClickListener(p -> {
					final Context context = getActivity();
					if (context != null) MethodShuttle.runInProfile(context, () ->	// ACTION_PROVISION_MANAGED_PROFILE is handled by IslandProvisioning.
						ContextCompat.startForegroundService(context, new Intent(ACTION_PROVISION_MANAGED_PROFILE).setPackage(Modules.MODULE_ENGINE))
					).exceptionally(t -> {	// Toast for success will be shown by IslandProvisioning.
						Toasts.show(activity, R.string.toast_internal_error, Toast.LENGTH_LONG);
						Analytics.$().logAndReport(TAG, "Error reprovisioning Island", t);
						return null;
					});
					return true;
				});
			} else pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed_other,
					R.drawable.ic_delete_forever_black_24dp, p -> startAccountSettingActivity());
		} else if (SDK_INT >= N && (disabled_profile = IslandManager.getManagedProfileIdIncludingDisabled(activity)) != 0) {
			final Optional<ComponentName> profile_owner_result = DevicePolicies.getProfileOwnerAsUser(activity, disabled_profile);
			if (profile_owner_result != null && profile_owner_result.isPresent() && Modules.MODULE_ENGINE.equals(profile_owner_result.get().getPackageName()))
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_incomplete,
						R.drawable.ic_build_black_24dp, preference -> startSetupActivityCleanly());
		} else {
			pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_pending_setup, R.drawable.ic_build_black_24dp, preference -> {
				if (SetupViewModel.checkManagedProvisioningPrerequisites(activity, true) == null)
					return startSetupActivityCleanly();		// Prefer ManagedProvision, which could also fallback to root routine.

				SafeAsyncTask.execute(activity, a -> Shell.SU.available(), su_available -> {
					final Activity activity_now = getActivity();
					if (activity_now == null) return;

					if (su_available) IslandSetup.requestProfileOwnerSetupWithRoot(activity_now);
					else WebContent.view(getActivity(), Uri.parse(Config.URL_SETUP.get()));
				});
				return true;
			});
		}
	}

	@Override public void onActivityResult(final int request, final int result, final Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_DEVICE_OWNER_ACTIVATION) IslandSetup.onAddAdminResult(getActivity());
	}

	private boolean startAccountSettingActivity() {
		Activities.startActivity(getActivity(), new Intent(Settings.ACTION_SYNC_SETTINGS));
		return true;
	}

	private boolean startSetupActivityCleanly() {
		// Finish all tasks of Island first to avoid state inconsistency.
		final ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			final List<ActivityManager.AppTask> tasks = am.getAppTasks();
			if (tasks != null) for (final ActivityManager.AppTask task : tasks) task.finishAndRemoveTask();
		}

		Activities.startActivity(getActivity(), new Intent(getActivity(), SetupActivity.class));
		return true;
	}

	public SetupPreferenceFragment() { super(R.xml.pref_setup); }

	static final String TAG = "Island.SPF";
}
