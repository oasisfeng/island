package com.oasisfeng.island;

import android.app.Application;
import android.content.Intent;
import android.content.ServiceConnection;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.shuttle.ServiceShuttle;
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

	@Override public boolean bindService(final Intent service, final ServiceConnection conn, final int flags) {
		return ServiceShuttle.bindService(this, service, conn, flags) || super.bindService(service, conn, flags);
	}

	@Override public void unbindService(final ServiceConnection conn) {
		if (! ServiceShuttle.unbindService(getBaseContext(), conn)) super.unbindService(conn);
	}
}
