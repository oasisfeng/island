package com.oasisfeng.island;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.console.apps.AppListFragment;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import java.util.List;

import java8.util.Optional;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public class MainActivity extends Activity {

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final IslandManager island = new IslandManager(this);
		if (Users.isProfile()) {	// Should generally not run in profile, unless the managed profile provision is interrupted or manually provision is not complete.
			if (! island.isProfileOwnerActive()) {
				Analytics.$().event("inactive_device_admin").send();
				startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
						.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(this))
						.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_reactivate_message)));
			} else {
				final PackageManager pm = getPackageManager();
				final List<ResolveInfo> resolve = pm.queryBroadcastReceivers(new Intent(Intent.ACTION_USER_INITIALIZE).setPackage(Modules.MODULE_ENGINE), 0);
				if (! resolve.isEmpty()) {
					Log.w(TAG, "Manual provisioning is pending, resume it now.");
					Analytics.$().event("profile_post_provision_pending").send();
					final ActivityInfo receiver = resolve.get(0).activityInfo;
					sendBroadcast(new Intent().setComponent(new ComponentName(receiver.packageName, receiver.name)));
				} else {    // Receiver disabled but launcher entrance is left enabled. The best bet is just disabling the launcher entrance. No provisioning attempt any more.
					Log.w(TAG, "Manual provisioning is finished, but launcher activity is still left enabled. Disable it now.");
					Analytics.$().event("profile_post_provision_activity_leftover").send();
					pm.setComponentEnabledSetting(new ComponentName(this, getClass()), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
				}
			}
			finish();
			return;
		}

		if (Analytics.$().setProperty("device_owner", island.isDeviceOwner())) {	// As device owner, always show main UI.
			startMainUi(savedInstanceState);
			return;
		}

		final UserHandle profile = Users.profile;
		if (profile == null) {				// Nothing setup yet
			showSetupWizard();
			return;
		}

		final Optional<Boolean> is_profile_owner = DevicePolicies.isProfileOwner(this, profile);
		if (is_profile_owner == null) { 	// Profile owner cannot be detected, the best bet is to continue to the main UI.
			startMainUi(savedInstanceState);
		} else if (! is_profile_owner.isPresent()) {	// Profile without owner, probably caused by provisioning interrupted before device-admin is activated.
			if (IslandManager.launchApp(this, getPackageName(), profile)) finish();	// Try starting myself in profile to finish the provisioning.
			else showSetupWizard();			// Cannot resume the provisioning, probably this profile is not created by us, go ahead with normal setup.
		} else if (is_profile_owner.get()) {
			final LauncherApps launcher_apps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
			final List<LauncherActivityInfo> our_activities_in_launcher = launcher_apps.getActivityList(getPackageName(), profile);
			if (! our_activities_in_launcher.isEmpty()) {	// Main activity is left enabled, probably due to pending post-provisioning in manual setup.
				Analytics.$().event("profile_provision_leftover").send();	// Some domestic ROMs may block implicit broadcast, causing ACTION_USER_INITIALIZE being dropped.
				Log.w(TAG, "Setup in Island is not complete, continue it now.");
				launcher_apps.startMainActivity(our_activities_in_launcher.get(0).getComponentName(), profile, null, null);
				finish();
			} else startMainUi(savedInstanceState);
		} else showSetupWizard();			// Profile is not owned by us, show setup wizard.
	}

	private void startMainUi(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) getFragmentManager().beginTransaction().replace(R.id.container, new AppListFragment()).commit();
	}

	private void showSetupWizard() {
		startActivity(new Intent(this, SetupActivity.class));
		finish();
	}

	private static final String TAG = MainActivity.class.getSimpleName();
}
