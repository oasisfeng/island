package com.oasisfeng.island;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.oasisfeng.island.console.apps.AppListFragment;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.setup.SetupProfileFragment;

public class MainActivity extends AppCompatActivity {

	public static boolean startInManagedProfile(final Context context, final UserHandle user) {
		final LauncherApps apps = (LauncherApps) context.getSystemService(LAUNCHER_APPS_SERVICE);
		final ComponentName activity = new ComponentName(context, MainActivity.class);
		if (! apps.isActivityEnabled(activity, user)) return false;
		apps.startMainActivity(activity, user, null, null);
		return true;
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final String this_pkg = getApplicationContext().getPackageName();
		final boolean is_device_owner = IslandManager.isDeviceOwner(this);
		if (! is_device_owner && ! getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
			Toast.makeText(this, "Sorry, your device or ROM does not support \"Managed Profile\" feature, which is required by Island.", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		if (savedInstanceState != null) {
			setContentView(R.layout.activity_main);
			return;
		}

		if (is_device_owner || IslandManager.isProfileOwner(this)) {
			// The managed profile is already set up and we are running inside it
			setContentView(R.layout.activity_main);
			showAppListFragment();
		} else {
			final String admin_class = IslandDeviceAdminReceiver.class.getName();
			final UserHandle profile = IslandManager.getManagedProfile(this);
            String owner_name = null;
			if (profile != null) {
				final ComponentName owner = IslandManager.getProfileOwner(this, profile);
				if (owner != null && this_pkg.equals(owner.getPackageName()) && owner.getClassName().equals(admin_class)) {
					if (startInManagedProfile(this, profile)) {
						overridePendingTransition(0, 0);
						finish();
						return;
					}	// Fall through if failed to start
				} else if (owner != null) try {
                    owner_name = getPackageManager().getApplicationInfo(owner.getPackageName(), 0).loadLabel(getPackageManager()).toString();
                } catch (final PackageManager.NameNotFoundException ignored) {}
            }
			setContentView(R.layout.activity_main);
			showSetupProfile(false, owner_name);
		}
	}

	private void showSetupProfile(final boolean has_other_owner, final String owner_name) {
		getFragmentManager().beginTransaction().replace(R.id.container, SetupProfileFragment.newInstance(has_other_owner, owner_name)).commit();
	}

	private void showAppListFragment() {
		getFragmentManager().beginTransaction().replace(R.id.container, new AppListFragment()).commit();
	}

	private static final String TAG = "Island.Main";
}
