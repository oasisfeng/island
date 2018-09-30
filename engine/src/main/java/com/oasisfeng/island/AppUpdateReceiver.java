package com.oasisfeng.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

/**
 * Handle {@link Intent#ACTION_MY_PACKAGE_REPLACED}
 *
 * Created by Oasis on 2017/7/20.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

	@Override public void onReceive(final Context context, final Intent intent) {
		// Currently, just blindly start the device owner provisioning, since it is idempotent, at least at present.
		if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
			if (Users.isOwner()) IslandProvisioning.startDeviceOwnerPostProvisioning(context, new DevicePolicies(context));
	}
}
