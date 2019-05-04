package com.oasisfeng.island;

import android.app.Application;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.oasisfeng.island.analytics.CrashReport;

import java.util.Map;

/**
 * For singleton instance purpose only.
 *
 * Created by Oasis on 2018/1/3.
 */
public class IslandApplication extends Application {

	public static Application $() {
		return sInstance;
	}

	@Override public Object getSystemService(final String name) {
		final Object service = mRegisteredServices.get(name);
		return service != null ? service : super.getSystemService(name);
	}

	@Override public PackageManager getPackageManager() {
		return mRegisteredPackageManager != null ? mRegisteredPackageManager : super.getPackageManager();
	}

	public IslandApplication() {
		if (sInstance != null) throw new IllegalStateException("Already initialized");
		sInstance = this;
		CrashReport.initCrashHandler();
	}

	void registerSystemService(final String name, final Object service) {
		mRegisteredServices.put(name, service);
	}

	void registerPackageManager(final PackageManager pm) {
		mRegisteredPackageManager = pm;
	}

	private final Map<String, Object> mRegisteredServices = new ArrayMap<>();
	private PackageManager mRegisteredPackageManager;

	private static IslandApplication sInstance;
}
