package com.oasisfeng.island;

import android.app.Application;

import com.oasisfeng.island.analytics.CrashReport;
import com.oasisfeng.island.firebase.FirebaseServiceProxy;
import com.oasisfeng.island.shared.R;

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

	@Override public void onCreate() {
		super.onCreate();
		final String firebase_proxy_host = getString(R.string.firebase_proxy_host);
		if (! firebase_proxy_host.isEmpty()) FirebaseServiceProxy.initialize(firebase_proxy_host);
	}

	private static IslandApplication sInstance;
}
