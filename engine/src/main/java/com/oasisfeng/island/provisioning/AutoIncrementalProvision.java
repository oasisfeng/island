package com.oasisfeng.island.provisioning;

import com.oasisfeng.android.os.Loopers;
import com.oasisfeng.island.engine.BuildConfig;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.pattern.PseudoContentProvider;
import com.oasisfeng.perf.Performances;
import com.oasisfeng.perf.Stopwatch;

import java9.util.Optional;

/**
 * Perform incremental provision
 *
 * Created by Oasis on 2017/11/21.
 */
public class AutoIncrementalProvision extends PseudoContentProvider {

	@Override public boolean onCreate() {
		final Stopwatch stopwatch = Performances.startUptimeStopwatch();
		if (Users.isOwner()) {
			Loopers.addIdleTask(() -> IslandProvisioning.startDeviceOwnerPostProvisioning(context()));	// isDeviceOwner() is checked inside.
		} else if (Users.isProfile()) {
			final Optional<Boolean> result = DevicePolicies.isProfileOwner(context(), Users.profile);
			if (result == null || ! result.isPresent() || ! result.get()) return false;		// Including the case that profile is not enabled yet. (during the broadcast ACTION_PROFILE_PROVISIONING_COMPLETE)
			final Thread thread = new Thread(this::startInProfile);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}
		if (BuildConfig.DEBUG) Performances.check(stopwatch, 5, "IncPro.MainThread");
		return false;
	}

	@ProfileUser private void startInProfile() {
		final Stopwatch stopwatch = Performances.startUptimeStopwatch();
		IslandProvisioning.performIncrementalProfileOwnerProvisioningIfNeeded(context());
		if (BuildConfig.DEBUG) Performances.check(stopwatch, 10, "IncPro.WorkerThread");
	}
}
