package com.oasisfeng.island.analytics;

import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import com.oasisfeng.island.firebase.FirebaseWrapper;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Wrapper for local performance trace
 *
 * Created by Oasis on 2017/7/18.
 */
@ParametersAreNonnullByDefault
class PerformanceTrace implements Analytics.Trace {

	static Analytics.Trace startTrace(final String name) {
		return new PerformanceTrace(FirebasePerformance.startTrace(name));
	}

	@Override public void start() {
		mTrace.start();
	}

	@Override public void stop() {
		mTrace.stop();
	}

	@Override public void incrementCounter(final String counter_name, final long increment_by) {
		mTrace.incrementCounter(counter_name, increment_by);
	}

	private PerformanceTrace(final Trace trace) {
		mTrace = trace;
	}

	private final Trace mTrace;

	static {
		FirebaseWrapper.init();		// Ensure Firebase is lazily initialized first.
	}
}
