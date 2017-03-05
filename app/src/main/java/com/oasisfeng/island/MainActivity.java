package com.oasisfeng.island;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.Nullable;

import com.oasisfeng.android.service.Services;
import com.oasisfeng.island.console.apps.AppListFragment;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.setup.SetupActivity;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import java.util.List;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public class MainActivity extends Activity {

	public static boolean startAsUser(final Context context, final UserHandle user) {
		final LauncherApps apps = (LauncherApps) context.getSystemService(LAUNCHER_APPS_SERVICE);
		final ComponentName activity = new ComponentName(context, MainActivity.class);
		if (! apps.isActivityEnabled(activity, user)) return false;
		apps.startMainActivity(activity, user, null, null);
		return true;
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final IslandManager island = new IslandManager(this);
		if (Users.isProfile()) {	// Should generally not running in profile, unless the managed-profile provision is interrupted or manually performed.
			if (! island.isProfileOwnerActive()) {
//				Analytics.$().event("inactive_device_admin").send();
				startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
						.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(this))
						.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_reactivate_message)));
				// TODO: Check result
				finish();
				return;
			}
			final ProgressDialog progress = ProgressDialog.show(this, null, getString(R.string.dialog_provision_in_progress), true/* indeterminate */, false/* cancelable */);
			// Bind to the IslandManager, triggering IslandProvisioning.startProfileOwnerProvisioningIfNeeded().
			Services.use(this, IIslandManager.class, IIslandManager.Stub::asInterface, service -> {
				getPackageManager().setComponentEnabledSetting(new ComponentName(this, MainActivity.class), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
				progress.cancel(); finish();		// Binder is returned when the provisioning is done.
			});
			return;
		}

		GlobalStatus.device_owner = island.isDeviceOwner();
		final UserHandle profile = GlobalStatus.profile = IslandManager.getManagedProfile(this);

		if (profile != null) {
			final ComponentName profile_owner = IslandManager.getProfileOwner(this, profile);
			if (profile_owner == null) {	// Profile without owner, probably caused by provisioning interrupted before device-admin is activated.
				if (IslandManager.launchApp(this, getPackageName(), profile)) {        // Try starting myself in profile to finish the provisioning.
					finish();
					return;
				} else if (! GlobalStatus.device_owner) {
					showSetupWizard();		// Cannot resume the provisioning, probably this profile is not created by us, go ahead with normal setup.
					return;
				}
			} else if (profile_owner.getPackageName().equals(Modules.MODULE_ENGINE)) {
				final LauncherApps launcher_apps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
				final List<LauncherActivityInfo> our_activities_in_launcher = launcher_apps.getActivityList(getPackageName(), profile);
				if (! our_activities_in_launcher.isEmpty()) {		// Main activity is left enabled, probably due to unfinished provisioning
					launcher_apps.startMainActivity(our_activities_in_launcher.get(0).getComponentName(), profile, null, null);
					finish();
					return;
				}
			} else if (! GlobalStatus.device_owner) {		// Profile was not created by us, show setup wizard if not device admin.
				showSetupWizard();
				return;
			}
		} else if (! GlobalStatus.device_owner) showSetupWizard();

		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			mAppListFragment = new AppListFragment();
			getFragmentManager().beginTransaction().replace(R.id.container, mAppListFragment).commit();
		}
	}

	@Override public void onBackPressed() {
		if (mAppListFragment != null && mAppListFragment.onBackPressed()) return;
		super.onBackPressed();
	}

	private void showSetupWizard() {
		startActivity(new Intent(this, SetupActivity.class));
		finish();
	}

	private @Nullable AppListFragment mAppListFragment;

}
