package com.oasisfeng.island.analytics;

import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.island.IslandApplication;
import com.oasisfeng.island.shared.BuildConfig;

import java.util.function.Supplier;


/**
 * Lazy initializer for crash handler.
 *
 * Created by Oasis on 2017/7/14.
 */
public abstract class CrashReport {

	static void logException(final Throwable t) { sSingleton.get().recordException(t); }
	static void log(final String message) { sSingleton.get().log(message); }
	static void setProperty(final String key, final String value) { sSingleton.get().setCustomKey(key, value); }
	static void setProperty(final String key, final int value) { sSingleton.get().setCustomKey(key, value); }
	static void setProperty(final String key, final boolean value) { sSingleton.get().setCustomKey(key, value); }

	private static final Supplier<FirebaseCrashlytics> sSingleton = Suppliers.memoize(() -> {
		FirebaseApp.initializeApp(IslandApplication.get());
		final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
		crashlytics.setCrashlyticsCollectionEnabled(true/*BuildConfig.CRASHLYTICS_ENABLED*/);
		crashlytics.setCustomKey("user", Process.myUserHandle().hashCode());			// Attach the current (Android) user ID to crash report.
		return crashlytics;
	});

	public static void initCrashHandler() {
		final Thread.UncaughtExceptionHandler current_exception_handler = Thread.getDefaultUncaughtExceptionHandler();
		if (! (current_exception_handler instanceof LazyThreadExceptionHandler))
			Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(current_exception_handler));
	}

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final @NonNull Thread thread, final @NonNull Throwable e) {
			if (BuildConfig.DEBUG) Log.e(TAG, "Handling:", e);
			if (mHandlingUncaughtException) {		// Avoid infinite recursion
				mOriginalHandler.uncaughtException(thread, e);
				return;
			}
			mHandlingUncaughtException = true;

			sSingleton.get();	// Initialize if not yet

			final Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
			if (handler != null) handler.uncaughtException(thread, e);	// May re-enter this method if delegate is initialized above.
			mHandlingUncaughtException = false;
		}

		LazyThreadExceptionHandler(final Thread.UncaughtExceptionHandler default_handler) {
			mOriginalHandler = default_handler;
		}

		private final Thread.UncaughtExceptionHandler mOriginalHandler;
		private boolean mHandlingUncaughtException;
	}

	private static final String TAG = "Island.Crash";
}
