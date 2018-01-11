package com.oasisfeng.island.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
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

import com.google.common.base.Joiner;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.setup.SetupViewModel;
import com.oasisfeng.island.setup.Shutdown;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.settings.ActionButtonPreference;

import java.io.File;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;
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
			pref_setup_mainland.setSummaryAndActionButton(R.string.pref_setup_mainland_summary_not_managed, R.drawable.ic_build_black_24dp, p -> startDeviceOwnerSetup());
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

	private boolean startDeviceOwnerSetup() {
		Dialogs.buildAlert(getActivity(), R.string.pref_setup_mainland_activate_title, R.string.pref_setup_mainland_activate_text)
				.setPositiveButton(R.string.dialog_button_continue, (d, w) -> tryActivatingDeviceOwnerWithRoot()).show();
		return true;
	}

	private void tryActivatingDeviceOwnerWithRoot() {
		final Activity activity = getActivity();
		String content = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><device-owner package=\"" + Modules.MODULE_ENGINE + "\" />";
		final Optional<Boolean> is_profile_owner;
		if (Users.profile != null && (is_profile_owner = DevicePolicies.isProfileOwner(activity, Users.profile)) != null && is_profile_owner.orElse(false))
			content += "<profile-owner package=\"" + Modules.MODULE_ENGINE + "\" name=\"Island\" userId=\"" + Users.toId(Users.profile)
					+ "\" component=\"" + DeviceAdmins.getComponentName(activity).flattenToString() + "\" />";
		content = content.replace("\"", "\\\"").replace("'", "\\'")
				.replace("<", "\\<").replace(">", "\\>");

		final String file = new File(Hacks.Environment_getSystemSecureDirectory.invoke().statically(), "device_owner.xml").getAbsolutePath();
		final String command = "echo " + content + " > " + file + " && chmod 600 " + file + " && chown system:system " + file;

		SafeAsyncTask.execute(activity, a -> Shell.SU.run(command), output -> {
			final Activity activity_now = getActivity();
			if (activity_now == null) return;
			if (output == null) {
				Toast.makeText(activity_now, R.string.toast_setup_god_mode_non_root, Toast.LENGTH_LONG).show();
				WebContent.view(activity_now, Uri.parse(Config.URL_SETUP.get()));
				return;
			}
			Analytics.$().event("activate_device_owner_root").with(Analytics.Param.CONTENT, Joiner.on('\n').join(output)).send();
			startActivityForResult(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
					.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(activity_now))
					.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_mainland_device_admin)), REQUEST_ADD_DEVICE_ADMIN);
		});
	}
	private static final int REQUEST_ADD_DEVICE_ADMIN = 1;

	@Override public void onActivityResult(final int request, final int result, final Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_ADD_DEVICE_ADMIN && new DevicePolicies(getActivity()).isAdminActive())
			Dialogs.buildAlert(getActivity(), 0, R.string.dialog_mainland_setup_done).withCancelButton()
					.setPositiveButton(R.string.dialog_button_reboot, (d, w) -> SafeAsyncTask.execute(() -> Shell.SU.run("reboot"))).show();
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
