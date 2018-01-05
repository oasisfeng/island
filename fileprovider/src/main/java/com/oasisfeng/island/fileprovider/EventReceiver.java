package com.oasisfeng.island.fileprovider;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.oasisfeng.island.shuttle.ContextShuttle;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Respond to events:
 *
 * MY_PACKAGE_REPLACED
 * MAIN_UI_RESUMED
 *
 * Created by Oasis on 2017/8/31.
 */
public class EventReceiver extends BroadcastReceiver {

	@Override public void onReceive(final Context context, final Intent intent) {
		Log.d(TAG, "Event: " + intent);
		if (! Users.hasProfile()) return;
		final ComponentName shuttle = new ComponentName(context, ShuttleProvider.class);

		final boolean iau_granted = Permissions.has(context, INTERACT_ACROSS_USERS);
		final boolean all_met = iau_granted && Permissions.has(context, WRITE_EXTERNAL_STORAGE)
				&& ExternalStorageProviderProxy.findTargetProvider(context) != null;

		// FIXME: Not enabling shuttle in owner user, as ExternalStorageProviderProxy is not working as expected in manger profile.
		//setComponentEnabled(context.getPackageManager(), shuttle, all_met);

		final PackageManager profile_pm;
		if (iau_granted && (profile_pm = ContextShuttle.getPackageManagerAsUser(context, Users.profile)) != null)
			setComponentEnabled(profile_pm, shuttle, all_met);		// Shuttle in profile
	}

	private static void setComponentEnabled(final PackageManager pm, final ComponentName component, final boolean enabled) {
		pm.setComponentEnabledSetting(component, enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DEFAULT/* not DISABLED */, DONT_KILL_APP);
	}

	private static final String TAG = "FP.EvtReceiver";
}
