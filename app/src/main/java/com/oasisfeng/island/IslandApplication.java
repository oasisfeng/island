package com.oasisfeng.island;

import android.app.Application;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Users;

/**
 * Application class
 *
 * Created by Oasis on 2016/5/6.
 */
public class IslandApplication extends Application {

	@Override public void onCreate() {
		super.onCreate();
		if (Users.isOwner()) Analytics.setContext(this);
	}
}
