package com.oasisfeng.island.analytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.intellij.lang.annotations.Pattern;

/**
 * Abstraction for analytics service
 *
 * Created by Oasis on 2016/5/26.
 */

public class Analytics {

	public void setProperty(final String key, final String value) {
		mAnalytics.setUserProperty(key, value);
	}

	public boolean setProperty(final String key, final boolean value) {
		setProperty(key, Boolean.toString(value));
		return value;
	}

	public interface Event {
		@CheckResult Event with(String key, String value);
		@CheckResult Event with(String key, long value);
		@CheckResult Event with(String key, double value);
		void send();
	}

	public @CheckResult Event event(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String event) {
		final Bundle bundle = new Bundle();
		return new Event() {

			@Override public @CheckResult Event with(final String key, final String value) {
				bundle.putString(key, value);
				return this;
			}

			@Override public @CheckResult Event with(final String key, final long value) {
				bundle.putLong(key, value);
				return this;
			}

			@Override public @CheckResult Event with(final String key, final double value) {
				bundle.putDouble(key, value);
				return this;
			}

			@Override public void send() {
				reportEventInternal(event, bundle.isEmpty() ? null : bundle);
			}
		};
	}

	private synchronized Analytics reportEventInternal(final @NonNull String event, final @Nullable Bundle params) {
		Log.d(TAG, "Event [" + event + "]: " + params);
		mAnalytics.logEvent(event, params);
		return this;
	}

	public static void setContext(final Context context) {
		sSingletonSupplier = Suppliers.memoize(() -> new Analytics(context.getApplicationContext()));
	}

	public static Analytics $() {
		return sSingletonSupplier.get();
	}

	private Analytics(final Context context) {
		mAnalytics = FirebaseAnalytics.getInstance(context);
	}

	private static Supplier<Analytics> sSingletonSupplier = () -> { throw new IllegalStateException("Context is not set yet"); };
	private static final String TAG = "Analytics";

	private final FirebaseAnalytics mAnalytics;
}
