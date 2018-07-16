package com.oasisfeng.island.engine;

import android.content.pm.ApplicationInfo;

interface IIslandManager {

	ApplicationInfo getApplicationInfo(String pkg, int flags);
	boolean enableSystemApp(String pkg);
	int cloneUserApp(String pkg, String apk_path, boolean do_it);
	/** @return whether the package is frozen, or true if not found */
	boolean freezeApp(String pkg, String reason);
	/** @return whether the package is unfrozen, or false if not found */
	boolean unfreezeApp(String pkg);
	String launchApp(String pkg);
	boolean block(String pkg);
	boolean unblock(String pkg);
	void provision();
}
