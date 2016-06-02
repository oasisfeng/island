package com.oasisfeng.island;

import android.app.Application;

import com.oasisfeng.island.analytics.Analytics;

/**
 * Application class
 *
 * Created by Oasis on 2016/5/6.
 */
public class IslandApplication extends Application {

	@Override public void onCreate() {
		super.onCreate();
		Analytics.setContext(this);
	}
}
