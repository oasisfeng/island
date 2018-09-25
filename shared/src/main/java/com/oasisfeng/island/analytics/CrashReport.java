package com.oasisfeng.island.analytics;

import com.crashlytics.android.core.CrashlyticsCore;
import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.island.firebase.FirebaseWrapper;
import com.oasisfeng.island.shared.BuildConfig;

import io.fabric.sdk.android.Fabric;

/**
 * Lazy initializer for crash handler.
 *
 * Created by Oasis on 2017/7/14.
 */
public abstract class CrashReport {

	private static final boolean DISABLED = BuildConfig.DEBUG;

	static void logException(final Throwable t) { sSingleton.get().logException(t); }
	static void log(final String message) { sSingleton.get().log(message); }
	static void setProperty(final String key, final String value) { sSingleton.get().setString(key, value); }
	static void setProperty(final String key, final int value) { sSingleton.get().setInt(key, value); }
	static void setProperty(final String key, final boolean value) { sSingleton.get().setBool(key, value); }

	private static final Supplier<CrashlyticsCore> sSingleton = Suppliers.memoize(() -> {
		Fabric.with(new Fabric.Builder(FirebaseWrapper.init()).debuggable(BuildConfig.DEBUG)
				.kits(new CrashlyticsCore.Builder().disabled(DISABLED).build()).build());
		return CrashlyticsCore.getInstance();
	});

	public static void initCrashHandler() {
		if (DISABLED) return;
		final Thread.UncaughtExceptionHandler current_exception_handler = Thread.getDefaultUncaughtExceptionHandler();
		if (! (current_exception_handler instanceof LazyThreadExceptionHandler))
			Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(current_exception_handler));
	}

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final Thread thread, final Throwable e) {
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
}
