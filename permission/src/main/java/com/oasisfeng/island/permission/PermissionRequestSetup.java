package com.oasisfeng.island.permission;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.google.common.base.Stopwatch;
import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.pattern.PseudoContentProvider;
import com.oasisfeng.perf.Performances;

import java9.util.Optional;

import static android.content.Intent.ACTION_MANAGED_PROFILE_ADDED;
import static android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Setup the enabled state of {@link PermissionRequestActivity} according to availability.
 *
 * Created by Oasis on 2017/8/15.
 */
public class PermissionRequestSetup extends PseudoContentProvider {

	@Override public boolean onCreate() {
		if (SDK_INT < M) return false;
		Looper.getMainLooper().getQueue().addIdleHandler(new MessageQueue.IdleHandler() { @RequiresApi(M) @Override public boolean queueIdle() {
			final Stopwatch stopwatch = Performances.startUptimeStopwatch();
			setup(context());
			Performances.check(stopwatch, 1, TAG);

			context().registerReceiver(mStateObserver, IntentFilters.forActions(
					DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED, ACTION_MANAGED_PROFILE_ADDED, ACTION_MANAGED_PROFILE_REMOVED));
			return false;
		}});
		return true;
	}

	static boolean setup(final Context context) {
		final Optional<Boolean> is_profile_owner = DevicePolicies.isProfileOwner(context);
		final boolean ready = is_profile_owner != null && is_profile_owner.orElse(false) || new DevicePolicies(context).isDeviceOwner();
		context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, PermissionRequestActivity.class),
				ready ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		return ready;
	}

	private final BroadcastReceiver mStateObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		Log.d(TAG, "Re-setup upon " + intent.getAction());
		setup(context);
	}};

	private static final String TAG = "PermissionRequestSetup";
}
