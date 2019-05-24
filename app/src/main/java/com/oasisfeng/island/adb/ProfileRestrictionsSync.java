package com.oasisfeng.island.adb;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.util.Log;

import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;

/**
 * Sync certain user restrictions upon profile starting
 *
 * Created by Oasis on 2019-5-24.
 */
public class ProfileRestrictionsSync extends BroadcastReceiver {

	@Override public void onReceive(final Context context, final Intent intent) {
		final String action = intent.getAction();
		if (! Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && ! Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
		final DevicePolicies policies = new DevicePolicies(context);
		if (Users.isOwner() || ! policies.isProfileOwner()) {	// This receiver is not needed in owner user or profile not managed by Island.
			context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, getClass()),
					PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
			return;
		}
		final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
		final Bundle owner_restrictions = um.getUserRestrictions(Users.owner);
		final Bundle profile_restrictions = SDK_INT >= N ? policies.invoke(DevicePolicyManager::getUserRestrictions)
				: um.getUserRestrictions(Process.myUserHandle());
		final boolean enabled_in_owner = owner_restrictions.getBoolean(DISALLOW_DEBUGGING_FEATURES);
		if (profile_restrictions.getBoolean(DISALLOW_DEBUGGING_FEATURES) != enabled_in_owner) {
			policies.setUserRestriction(DISALLOW_DEBUGGING_FEATURES, enabled_in_owner);
			Log.i(TAG, "Synchronized: " + DISALLOW_DEBUGGING_FEATURES + " = " + enabled_in_owner);
		}
	}

	private static final String TAG = "Island.PRS";
}
