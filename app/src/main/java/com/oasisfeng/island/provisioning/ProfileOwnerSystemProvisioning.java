package com.oasisfeng.island.provisioning;

import com.oasisfeng.island.engine.IslandManager;

/**
 * Simulate the managed provisioning procedure for manually enabled managed profile.
 *
 * Created by Oasis on 2016/4/18.
 */
class ProfileOwnerSystemProvisioning {

	private static final String MANAGED_PROFILE_CONTACT_REMOTE_SEARCH = "managed_profile_contact_remote_search";

	public static void start(final IslandManager island) {
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
//			Settings.Secure.putInt(context.getContentResolver(), MANAGED_PROFILE_CONTACT_REMOTE_SEARCH, 1);

		/* DISALLOW_WALLPAPER cannot be changed, we cannot add this restriction. */
		// Set default cross-profile intent-filters
		CrossProfileIntentFiltersHelper.setFilters(island);
	}
}
