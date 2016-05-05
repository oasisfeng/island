package com.oasisfeng.island;

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * A collection of Google Analytics trackers. Fetch the tracker you need using {@code AnalyticsTrackers.getInstance().get(...)}
 */
public final class AnalyticsTrackers {

	public enum Target {
		APP,
		// Add more trackers here if you need, and update the code in #get(Target) below
	}

	private static AnalyticsTrackers sInstance;

	public static synchronized void initialize(final Context context) {
		if (sInstance != null) throw new IllegalStateException("Extra call to initialize analytics trackers");
		sInstance = new AnalyticsTrackers(context);
	}

	public static synchronized AnalyticsTrackers getInstance() {
		if (sInstance == null) throw new IllegalStateException("Call initialize() before getInstance()");
		return sInstance;
	}

	private final Map<Target, Tracker> mTrackers = new HashMap<>();
	private final Context mContext;

	/** Don't instantiate directly - use {@link #getInstance()} instead. */
	private AnalyticsTrackers(final Context context) { mContext = context.getApplicationContext(); }

	public synchronized Tracker get(final Target target) {
		if (! mTrackers.containsKey(target)) {
			final Tracker tracker;
			switch (target) {
			case APP:
				tracker = GoogleAnalytics.getInstance(mContext).newTracker(R.xml.app_tracker);
				tracker.enableAdvertisingIdCollection(true);
				break;
			default:
				throw new IllegalArgumentException("Unhandled analytics target " + target);
			}
			mTrackers.put(target, tracker);
		}
		return mTrackers.get(target);
	}
}
