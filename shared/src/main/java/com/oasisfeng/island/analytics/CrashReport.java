package com.oasisfeng.island.analytics;

import com.crashlytics.android.Crashlytics;
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

	static CrashlyticsCore $() { return sSingleton.get(); }

	private static final Supplier<CrashlyticsCore> sSingleton = Suppliers.memoize(() -> {
		Fabric.with(new Fabric.Builder(FirebaseWrapper.init()).kits(new Crashlytics()).debuggable(BuildConfig.DEBUG).build());
		return CrashlyticsCore.getInstance();
	});

	public static void initCrashHandler() {
		if (BuildConfig.DEBUG) return;
		final Thread.UncaughtExceptionHandler current_exception_handler = Thread.getDefaultUncaughtExceptionHandler();
		if (! (current_exception_handler instanceof LazyThreadExceptionHandler))
			Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(current_exception_handler));
	}

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final Thread t, final Throwable e) {
			if (Thread.getDefaultUncaughtExceptionHandler() instanceof LazyThreadExceptionHandler)
				Thread.setDefaultUncaughtExceptionHandler(mDefaultHandler);	// Revert global exception handler before initializing crash report service.

			$();	// Initialize if not yet
			final Thread.UncaughtExceptionHandler handler = t.getUncaughtExceptionHandler();
			if (handler != null) handler.uncaughtException(t, e);
		}

		LazyThreadExceptionHandler(final Thread.UncaughtExceptionHandler default_handler) {
			mDefaultHandler = default_handler;
		}

		private final Thread.UncaughtExceptionHandler mDefaultHandler;
	}
}
