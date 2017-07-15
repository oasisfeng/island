package com.oasisfeng.island.analytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.intellij.lang.annotations.Pattern;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The analytics implementation in local process
 *
 * Created by Oasis on 2017/3/23.
 */
@ParametersAreNonnullByDefault
class AnalyticsImpl implements Analytics {

	@Override public @CheckResult Event event(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String event) {
		final Bundle bundle = new Bundle();
		return new Event() {
			@Override public @CheckResult Event with(final Param key, final String value) { bundle.putString(key.key, value); return this; }
			@Override public void send() { reportEvent(event, bundle); }
		};
	}

	@Override public void report(final Throwable t) {
		CrashReport.$().logException(t);
	}

	@Override public void reportEvent(final String event, final Bundle params) {
		Log.d(TAG, params.isEmpty() ? "Event: " + event : "Event: " + event + " " + params);
		mAnalytics.logEvent(event, params);
	}

	@Override public void setProperty(final String key, final String value) {
		mAnalytics.setUserProperty(key, value);
	}

	AnalyticsImpl(final Context context) {
		FirebaseApp.initializeApp(context);
		mAnalytics = FirebaseAnalytics.getInstance(context);
	}

	private final FirebaseAnalytics mAnalytics;

	private static final String TAG = "Analytics";
}
