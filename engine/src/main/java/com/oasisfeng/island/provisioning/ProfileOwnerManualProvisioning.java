package com.oasisfeng.island.provisioning;

import android.content.Context;
import android.util.Log;

import com.oasisfeng.island.provisioning.task.DeleteNonRequiredAppsTask;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;

import static com.oasisfeng.island.provisioning.task.DeleteNonRequiredAppsTask.PROFILE_OWNER;

/**
 * Simulate the managed provisioning procedure for manually enabled managed profile.
 *
 * Created by Oasis on 2016/4/18.
 */
class ProfileOwnerManualProvisioning {

	@ProfileUser static void start(final Context context, final DevicePolicies policies) {
		new DeleteNonRequiredAppsTask(context, context.getPackageName(), PROFILE_OWNER, true, Users.toId(Users.current()), false, new DeleteNonRequiredAppsTask.Callback() {
			@Override public void onSuccess() {}
			@Override public void onError() { Log.e(TAG, "Error provisioning (task 1)"); }
		}).run();

		/* DisableBluetoothSharingTask & DisableInstallShortcutListenersTask cannot be done here, since they disable components. */
		/* Settings.Secure.MANAGED_PROFILE_CONTACT_REMOTE_SEARCH can be toggled in system Settings - Users & Profiles - Profile Settings */
		/* DISALLOW_WALLPAPER cannot be changed by profile / device owner. */

		// Set default cross-profile intent-filters
		CrossProfileIntentFiltersHelper.setFilters(policies);
	}

	private static final String TAG = ProfileOwnerManualProvisioning.class.getSimpleName();
}
