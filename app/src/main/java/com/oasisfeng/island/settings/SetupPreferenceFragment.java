package com.oasisfeng.island.settings;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.preference.Preference;
import android.provider.Settings;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.setup.Shutdown;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.settings.ActionButtonPreference;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
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
		if (new IslandManager(activity).isDeviceOwner()) {
			pref_setup_mainland.setSummaryAndActionButton(R.string.pref_setup_mainland_summary_managed, R.drawable.ic_cancel_black_24dp, this::startDeviceOwnerDeactivation);
		} else pref_setup_mainland.setSummaryAndNotSelectable(R.string.pref_setup_mainland_summary_not_managed);

		final ActionButtonPreference pref_island = (ActionButtonPreference) findPreference(getString(R.string.key_setup_island));
		final UserHandle profile = IslandManager.getManagedProfile(activity);
		final int disabled_profile;
		if (profile != null) {
			final ComponentName profile_owner = IslandManager.getProfileOwner(activity, profile);
			if (profile_owner != null && Modules.MODULE_ENGINE.equals(profile_owner.getPackageName())) {    // Normal (managed by Island)
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed, R.drawable.ic_delete_forever_black_24dp, this::startProfileShutdown);
			} else pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_managed_other,
					R.drawable.ic_delete_forever_black_24dp, this::startAccountSettingActivity);
		} else if (SDK_INT >= N && (disabled_profile = IslandManager.getManagedProfileWithDisabled(activity)) != 0) {
			final ComponentName profile_owner = IslandManager.getProfileOwner(activity, disabled_profile);
			if (profile_owner != null && Modules.MODULE_ENGINE.equals(profile_owner.getPackageName()))
				pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_incomplete,
						R.drawable.ic_build_black_24dp, this::startSetupActivity);
		} else if (SDK_INT >= M && isDeviceManaged(activity)) {        // ManagedProvisioning refuses to create managed profile on managed device, since Android 6.0.
			pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_not_setup,
					R.drawable.ic_open_in_browser_black_24dp, this::showOnlineSetupGuide);
		} else pref_island.setSummaryAndActionButton(R.string.pref_setup_island_summary_pending_setup,
				R.drawable.ic_build_black_24dp, this::startSetupActivity);
	}

	private boolean isDeviceManaged(final Activity activity) {
		if (! Hacks.DevicePolicyManager_getDeviceOwner.isAbsent()) {
			final DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
			return Hacks.DevicePolicyManager_getDeviceOwner.invoke().on(dpm) != null;
		} else if (new IslandManager(getActivity()).isDeviceOwner()) return true;        // Fall-back check, only if we are the device owner.
		return false;
	}

	private boolean startProfileShutdown(final Preference preference) {
		Shutdown.requestProfileRemoval(getActivity());
		return true;
	}

	private boolean startDeviceOwnerDeactivation(final Preference preference) {
		Shutdown.requestDeviceOwnerDeactivation(getActivity());
		return true;
	}

	private boolean startAccountSettingActivity(final Preference preference) {
		Activities.startActivity(getActivity(), new Intent(Settings.ACTION_SYNC_SETTINGS));
		return true;
	}

	private boolean startSetupActivity(final Preference preference) {
		Activities.startActivity(getActivity(), new Intent(getActivity(), SetupActivity.class));
		return true;
	}

	private boolean showOnlineSetupGuide(final Preference preference) {
		WebContent.view(getActivity(), Uri.parse(Config.URL_SETUP.get()));
		return true;
	}

	public SetupPreferenceFragment() { super(R.xml.pref_setup); }
}
