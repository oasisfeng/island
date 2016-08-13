package com.oasisfeng.island;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.console.apps.AppListFragment;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.setup.SetupProfileFragment;

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
		final String this_pkg = getApplicationContext().getPackageName();
		final IslandManager island = new IslandManager(this);
		final boolean is_device_owner = island.isDeviceOwner();
		if (! is_device_owner && ! getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
			Toast.makeText(this, R.string.dialog_incompatible_rom, Toast.LENGTH_LONG).show();
			Analytics.$().event("compat-lack-managed-profile-feature").send();
			finish();
			return;
		}

		if (savedInstanceState != null) {
			setContentView(R.layout.activity_main);
			return;
		}

		final int user = Process.myUserHandle().hashCode();
		if ((user == 0 && is_device_owner) || (user != 0 && island.isProfileOwner())) {
			if (user != 0 && ! island.isProfileOwnerActive()) {        // Edge case: profile owner is set but device admin is not active. (May occur on MIUI if Island is uninstalled without managed profile removed first)
				Analytics.$().event("inactive-device-admin").send();
				startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
						.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, IslandDeviceAdminReceiver.getComponentName(this))
						.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_reactivate_message)));
				finish();
				return;
			}
			// The managed profile is already set up and we are running inside it
			setContentView(R.layout.activity_main);
			showAppListFragment();
		} else if (user == 0) {
			final String admin_class = IslandDeviceAdminReceiver.class.getName();
			final UserHandle profile = IslandManager.getManagedProfile(this);
			String owner_label = null;
			if (profile != null) {
				final ComponentName owner = IslandManager.getProfileOwner(this, profile);
				if (owner != null && this_pkg.equals(owner.getPackageName()) && owner.getClassName().equals(admin_class)) {
					if (startAsUser(this, profile)) {
						overridePendingTransition(0, 0);
						finish();
						return;
					}	// Fall through if failed to start
				} else if (owner != null) try {
                    owner_label = getPackageManager().getApplicationInfo(owner.getPackageName(), 0).loadLabel(getPackageManager()).toString();
					Analytics.$().event("profile-owner-existent").with("package", owner.getPackageName()).with("label", owner_label).send();
                } catch (final PackageManager.NameNotFoundException ignored) {}
            }
			setContentView(R.layout.activity_main);
			showSetupProfile(false, owner_label);
		} else/* if (user != 0 && ! island.isProfileOwner()) */{
			final Parcel parcel = Parcel.obtain();
			try {
				parcel.writeInt(0);
				final UserHandle owner = UserHandle.CREATOR.createFromParcel(parcel);
				startAsUser(this, owner);		// Start Island in owner user
				finish();
			} finally {
				parcel.recycle();
			}
		}
	}

	private void showSetupProfile(final boolean has_other_owner, final @Nullable String owner_name) {
		getFragmentManager().beginTransaction().replace(R.id.container, SetupProfileFragment.newInstance(has_other_owner, owner_name)).commit();
	}

	private void showAppListFragment() {
		getFragmentManager().beginTransaction().replace(R.id.container, new AppListFragment()).commit();
	}
}
