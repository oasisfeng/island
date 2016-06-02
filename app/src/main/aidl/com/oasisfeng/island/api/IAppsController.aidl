package com.oasisfeng.island.api;

interface IAppsController {
	/** @return stripped version of */
    List<String> getInstalledApps(int flags);
    /** @return the number of apps frozen actually */
    void freeze(String pkg);
}
