package com.oasisfeng.island.analytics;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.island.IslandApplication;
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
		Fabric.with(IslandApplication.$(), new Crashlytics());
		return CrashlyticsCore.getInstance();
	});

	public static void init() {
		if (BuildConfig.DEBUG) return;
		final Thread.UncaughtExceptionHandler current_exception_handler = Thread.getDefaultUncaughtExceptionHandler();
		if (! (current_exception_handler instanceof LazyThreadExceptionHandler))
			Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(current_exception_handler));
	}

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final Thread t, final Throwable e) {
			if (Thread.getDefaultUncaughtExceptionHandler() instanceof LazyThreadExceptionHandler)
				Thread.setDefaultUncaughtExceptionHandler(mDefaultHandler);	// Revert global exception handler before initializing crash report service.

			Fabric.with(IslandApplication.$(), new Crashlytics());

			final Thread.UncaughtExceptionHandler handler = t.getUncaughtExceptionHandler();
			if (handler != null) handler.uncaughtException(t, e);
		}

		LazyThreadExceptionHandler(final Thread.UncaughtExceptionHandler default_handler) {
			mDefaultHandler = default_handler;
		}

		private final Thread.UncaughtExceptionHandler mDefaultHandler;
	}
}
