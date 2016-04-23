package com.oasisfeng.island.provisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

/**
 * Simulate the managed provisioning procedure for manually enabled managed profile.
 *
 * Created by Oasis on 2016/4/18.
 */
public class ProfileOwnerSystemProvisioning {

	public static void start(final DevicePolicyManager dpm, final ComponentName admin) {
		/* DISALLOW_WALLPAPER cannot be changed, we cannot add this restriction. */
		// Set default cross-profile intent-filters
		CrossProfileIntentFiltersHelper.setFilters(dpm, admin);
	}
}
