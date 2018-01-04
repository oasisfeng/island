package com.oasisfeng.island.analytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.shared.R;

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
			@Override public @CheckResult Event withRaw(final String key, final @Nullable String value) { bundle.putString(key, value); return this; }
			@Override public void send() { reportEvent(event, bundle); }
		};
	}

	@Override public void report(final Throwable t) {
		CrashReport.$().logException(t);
	}

	@Override public void reportEvent(final String event, final Bundle params) {
		Log.d(TAG, params.isEmpty() ? "Event: " + event : "Event: " + event + " " + params);

		final HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder().setCategory(event);
		final String category = params.getString(Param.ITEM_CATEGORY.key);
		final String id = params.getString(Param.ITEM_ID.key);
		final String name = params.getString(Param.ITEM_NAME.key);
		if (category != null) {
			builder.setAction(category);
			if (BuildConfig.DEBUG && name != null) throw new IllegalArgumentException("Category and Name cannot be used at the same time: " + event);
		} else if (name != null) builder.setAction(name);
		if (id != null) builder.setLabel(id);
		mGoogleAnalytics.send(builder.build());

		mFirebaseAnalytics.logEvent(event, params);
	}

	@Override public void setProperty(final Property property, final String value) {
		mGoogleAnalytics.set("&cd" + property.ordinal() + 1, value);	// Custom dimension (index >= 1)
		mFirebaseAnalytics.setUserProperty(property.name, value);
	}

	AnalyticsImpl(final Context context) {
		final GoogleAnalytics google_analytics = GoogleAnalytics.getInstance(context);
		if (BuildConfig.DEBUG) google_analytics.setDryRun(true);
		mGoogleAnalytics = google_analytics.newTracker(R.xml.analytics_tracker);
		mGoogleAnalytics.enableAdvertisingIdCollection(true);

		FirebaseApp.initializeApp(context);
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
	}

	private final Tracker mGoogleAnalytics;
	private final FirebaseAnalytics mFirebaseAnalytics;

	private static final String TAG = "Analytics";
}
