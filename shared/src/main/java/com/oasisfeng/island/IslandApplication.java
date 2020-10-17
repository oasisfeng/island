package com.oasisfeng.island;

import android.app.Application;

import com.oasisfeng.island.analytics.CrashReport;

/**
 * For singleton instance purpose only.
 *
 * Created by Oasis on 2018/1/3.
 */
public class IslandApplication extends Application {

	public static Application $() {
		return sInstance;
	}

	public IslandApplication() {
		if (sInstance != null) throw new IllegalStateException("Already initialized");
		sInstance = this;
		CrashReport.initCrashHandler();
	}

	private static IslandApplication sInstance;
}
