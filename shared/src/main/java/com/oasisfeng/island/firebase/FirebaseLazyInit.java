package com.oasisfeng.island.firebase;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.google.firebase.FirebaseApp;

/**
 * Make the initialization of Firebase as lazy as possible.
 *
 * Created by Oasis on 2017/3/21.
 */
public class FirebaseLazyInit {

	public static void lazy(final Application app) {
		// For Firebase Crash Report
		Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(app));
		// For Firebase Analytics
		app.registerActivityLifecycleCallbacks(new LazyInitActivityLifecycleCallbacks(app));
	}

	public static void ensureInitialized(final Context context) {
		if (sInitialized) return;
		if (Thread.getDefaultUncaughtExceptionHandler() instanceof LazyThreadExceptionHandler)
			Thread.setDefaultUncaughtExceptionHandler(null);	// Always remove our lazy handler before initialization to avoid conflict.
		final long begin = SystemClock.uptimeMillis();
		FirebaseApp.initializeApp(context);
		final long duration = SystemClock.uptimeMillis() - begin;
		sInitialized = true;
		if (duration > 12) Log.d(TAG, duration + "ms");
	}

	private static volatile boolean sInitialized;
	private static final String TAG = "FirebaseInit";

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final Thread t, final Throwable e) {
			FirebaseLazyInit.ensureInitialized(app);
			final Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
			if (handler != null) handler.uncaughtException(t, e);
		}

		LazyThreadExceptionHandler(final Application app) { this.app = app; }

		private final Application app;
	}

	private static class LazyInitActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

		@Override public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
			app.unregisterActivityLifecycleCallbacks(this);
			ensureInitialized(app);
		}

		@Override public void onActivityStopped(final Activity activity) {}
		@Override public void onActivityStarted(final Activity activity) {}
		@Override public void onActivitySaveInstanceState(final Activity activity, final Bundle outState) {}
		@Override public void onActivityResumed(final Activity activity) {}
		@Override public void onActivityPaused(final Activity activity) {}
		@Override public void onActivityDestroyed(final Activity activity) {}
		LazyInitActivityLifecycleCallbacks(final Application app) { this.app = app; }

		private final Application app;
	}
}
