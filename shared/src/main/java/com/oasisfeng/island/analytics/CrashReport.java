package com.oasisfeng.island.analytics;

import android.content.Context;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.island.IslandApplication;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.pattern.PseudoContentProvider;

import io.fabric.sdk.android.Fabric;

/**
 * Lazy initializer for crash handler.
 *
 * Created by Oasis on 2017/7/14.
 */
public abstract class CrashReport extends PseudoContentProvider {

	static CrashlyticsCore $() { return sSingleton.get(); }

	private static final Supplier<CrashlyticsCore> sSingleton = Suppliers.memoize(() -> {
		Fabric.with(IslandApplication.$(), new Crashlytics());
		return CrashlyticsCore.getInstance();
	});

	@Override public boolean onCreate() {
		if (BuildConfig.DEBUG) return false;
		Thread.setDefaultUncaughtExceptionHandler(new LazyThreadExceptionHandler(context(), Thread.getDefaultUncaughtExceptionHandler()));
		return false;
	}

	private static class LazyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override public void uncaughtException(final Thread t, final Throwable e) {
			if (Thread.getDefaultUncaughtExceptionHandler() instanceof LazyThreadExceptionHandler)
				Thread.setDefaultUncaughtExceptionHandler(mDefaultHandler);	// Revert global exception handler before initializing crash report service.

			Fabric.with(mContext, new Crashlytics());

			final Thread.UncaughtExceptionHandler handler = t.getUncaughtExceptionHandler();
			if (handler != null) handler.uncaughtException(t, e);
		}

		LazyThreadExceptionHandler(final Context context, final Thread.UncaughtExceptionHandler default_handler) {
			mContext = context;
			mDefaultHandler = default_handler;
		}

		private final Context mContext;
		private final Thread.UncaughtExceptionHandler mDefaultHandler;
	}
}
