package com.oasisfeng.island.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.Preference;
import android.provider.Settings;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.setup.SetupViewModel;
import com.oasisfeng.island.setup.Shutdown;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.settings.ActionButtonPreference;

import java.util.List;

import java9.util.Optional;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Settings - Setup
 *
 * Created by Oasis on 2017/3/9.
 */
public class SetupPreferenceFragment extends SettingsActivity.SubPreferenceFragment {

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Activity activity = getActivity();

		final ActionButtonPreference pref_setup_mainland = (ActionButtonPreference) findPreference(getString(R.string.key_setup_mainland));
		final Preference pref_mainland_reprovisioning = findPreference(getString(R.string.key_setup_mainland_reprovision));
		if (new DevicePolicies(activity).isDeviceOwner()) {
			pref_setup_mainland.setSummaryAndActionButton(R.string.pref_setup_mainland_summary_managed, R.drawable.ic_cancel_black_24dp, p -> startDeviceOwnerDeactivation());

			pref_mainland_reprovisioning.setEnabled(true);
			pref_mainland_reprovisioning.setOnPreferenceClickListener(p -> {
				if (! IslandManager.useServiceInOwner(activity, island -> {
					island.provision();
					Toast.makeText(activity, R.string.toast_done, Toast.LENGTH_SHORT).show();
				})) Toast.makeText(activity, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
				return true;
			});
		} else {
			pref_setup_mainland.setSummaryAndNotSelectable(R.string.pref_setup_mainland_summary_not_managed);
			removeLeafPreference(getPreferenceScreen(), pref_mainland_reprovisioning);
		}

		final ActionButtonPreference pref_island = (ActionButtonPreference) findPreference(getString(R.string.key_setup_island));
		final UserHandle profile = DevicePolicies.getManagedProfile(activity);
		final int disabled_profile;
		if (profile != null) {
			final Optional<Boolean> is_profile_owner = DevicePolicies.isOwnerOfEnabledProfile(activity);
			if (is_profile_owner == null)
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_unknown, R.drawable.ic_delete_forever_black_24dp, p -> startAccountSettingActivity());
			else if (is_profile_owner.get()) {    // Normal (managed by Island)
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed, R.drawable.ic_delete_forever_black_24dp, p -> startProfileShutdown());
				final Preference pref_island_reprovisioning = findPreference(getString(R.string.key_setup_island_reprovision));
				pref_island_reprovisioning.setEnabled(true);
				pref_island_reprovisioning.setOnPreferenceClickListener(p -> {
					if (! IslandManager.useServiceInProfile(activity, island -> {
						final ProgressDialog progress_dialog = ProgressDialog.show(activity, null, getString(R.string.dialog_provision_in_progress));
						progress_dialog.show();
						try {
							island.provision();
							Toast.makeText(activity, R.string.toast_done, Toast.LENGTH_SHORT).show();
						} catch (final RemoteException e) {
							Toast.makeText(activity, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
						} finally {
							progress_dialog.dismiss();
						}
					})) Toast.makeText(activity, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
					return true;
				});
			} else pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed_other,
					R.drawable.ic_delete_forever_black_24dp, p -> startAccountSettingActivity());
		} else if (SDK_INT >= N && (disabled_profile = IslandManager.getManagedProfileIdIncludingDisabled(activity)) != 0) {
			final Optional<ComponentName> profile_owner_result = DevicePolicies.getProfileOwnerAsUser(activity, disabled_profile);
			if (profile_owner_result != null && profile_owner_result.isPresent() && Modules.MODULE_ENGINE.equals(profile_owner_result.get().getPackageName()))
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_incomplete,
						R.drawable.ic_build_black_24dp, preference -> startSetupActivity());
		} else if (SetupViewModel.checkManagedProvisioningPrerequisites(activity, true) == null) {
			pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_pending_setup,
					R.drawable.ic_build_black_24dp, preference -> startSetupActivity());
		} else pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_pending_manual_setup,
				R.drawable.ic_open_in_browser_black_24dp, preference -> showOnlineSetupGuide());
	}

	private boolean startProfileShutdown() {
		Shutdown.requestProfileRemoval(getActivity());
		return true;
	}

	private boolean startDeviceOwnerDeactivation() {
		Shutdown.requestDeviceOwnerDeactivation(getActivity());
		return true;
	}

	private boolean startAccountSettingActivity() {
		Activities.startActivity(getActivity(), new Intent(Settings.ACTION_SYNC_SETTINGS));
		return true;
	}

	private boolean startSetupActivity() {
		// Finish all tasks of Island first to avoid state inconsistency.
		final ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
		final List<ActivityManager.AppTask> tasks = am.getAppTasks();
		if (tasks != null) for (final ActivityManager.AppTask task : tasks) task.finishAndRemoveTask();

		Activities.startActivity(getActivity(), new Intent(getActivity(), SetupActivity.class));
		return true;
	}

	private boolean showOnlineSetupGuide() {
		WebContent.view(getActivity(), Uri.parse(Config.URL_SETUP.get()));
		return true;
	}

	public SetupPreferenceFragment() { super(R.xml.pref_setup); }
}
