package com.oasisfeng.island;

import android.app.Application;

/**
 * For singleton instance purpose only.
 *
 * Created by Oasis on 2018/1/3.
 */
public class IslandApplication extends Application {

	@Override public void onCreate() {
		super.onCreate();
		if (sInstance != null) throw new IllegalStateException("Already initialized");
		sInstance = this;
	}

	public static Application $() {
		return sInstance;
	}

	private static IslandApplication sInstance;
}
