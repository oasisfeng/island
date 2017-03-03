package com.oasisfeng.island.engine;

import android.content.pm.ApplicationInfo;

interface IIslandManager {

	List<ApplicationInfo> queryApps(int query_flags, int exclude_flags);
	ApplicationInfo getApplicationInfo(String pkg, int flags);
	int cloneApp(String pkg, boolean do_it);
	/** @return whether the package is frozen, or true if not found */
	boolean freezeApp(String pkg, String reason);
	/** @return whether the package is unfrozen, or false if not found */
	boolean unfreezeApp(String pkg);
	boolean launchApp(String pkg);
	boolean block(String pkg);
	boolean unblock(String pkg);
	/** Destroy the current profile. */
	void destroyProfile();
	/** Query the used packages during the given time span. (works on Android 6+ or Android 5.x with PACKAGE_USAGE_STATS permission granted manually) */
	String[] queryUsedPackagesDuring(long begin_time, long end_time);
}
