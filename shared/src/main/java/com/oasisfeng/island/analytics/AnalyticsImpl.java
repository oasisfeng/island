package com.oasisfeng.island.analytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.oasisfeng.island.shared.BuildConfig;

import org.intellij.lang.annotations.Pattern;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The analytics implementation in local process
 *
 * Created by Oasis on 2017/3/23.
 */
@ParametersAreNonnullByDefault
public class AnalyticsImpl extends Analytics {

	@Override public @CheckResult Event event(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String event) {
		final Bundle bundle = new Bundle();
		return new Event() {
			@Override public @CheckResult Event with(final Param key, final String value) { bundle.putString(key.key, value); return this; }
			@Override public void send() { reportEvent(event, bundle); }
		};
	}

	@Override public void report(final Throwable t) {
		if (BuildConfig.DEBUG) Log.e(TAG, "About to report", t);
		FirebaseCrash.report(t);
		// TODO: Verify the reach rate of the following redundant reporting via event.
		final Bundle bundle = new Bundle(); bundle.putString(FirebaseAnalytics.Param.LOCATION, t.getMessage());
		reportEvent("temp_error", bundle);
	}

	@Override public void reportEvent(final String event, final Bundle params) {
		Log.d(TAG, params.isEmpty() ? "Event: " + event : "Event: " + event + " " + params);
		mAnalytics.logEvent(event, params);
	}

	@Override public void setProperty(final String key, final String value) {
		mAnalytics.setUserProperty(key, value);
	}

	AnalyticsImpl(final Context context) {
		mAnalytics = FirebaseAnalytics.getInstance(context);
	}

	private final FirebaseAnalytics mAnalytics;

	private static final String TAG = "Analytics";
}
