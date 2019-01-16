package com.oasisfeng.perf;

import android.os.SystemClock;
import android.util.Log;

import com.oasisfeng.deagle.BuildConfig;

import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

/**
 * Utility class for performance related stuffs
 *
 * Created by Oasis on 2016/9/25.
 */
public class Performances {

	private static final boolean DEBUG = BuildConfig.DEBUG;

	public static final class Debug {

		public static @Nullable Stopwatch startUptimeStopwatch() {
			return DEBUG ? Stopwatch.createStarted(TICKER_UPTIME) : null;
		}

		public static @Nullable Stopwatch startThreadCpuTimeStopwatch() {
			return DEBUG ? Stopwatch.createStarted(TICKER_THREAD_CPU_TIME) : null;
		}
	}

	public static final Ticker TICKER_UPTIME = new Ticker() { @Override public long read() { return SystemClock.uptimeMillis() * 1_000_000L; }};
	public static final Ticker TICKER_THREAD_CPU_TIME = new Ticker() { @Override public long read() { return android.os.Debug.threadCpuTimeNanos(); }};

	public static @Nullable Stopwatch startUptimeStopwatch() {
		return Stopwatch.createStarted(TICKER_UPTIME);
	}

	public static @Nullable Stopwatch startThreadCpuTimeStopwatch() {
		return Stopwatch.createStarted(TICKER_THREAD_CPU_TIME);
	}

	public static void check(final @Nullable Stopwatch stopwatch, final long threshold_millis, final String what) {
		if (stopwatch == null) return;
		final long duration = stopwatch.elapsed(TimeUnit.MILLISECONDS);
		if (duration > threshold_millis) Log.w(TAG, duration + " ms spent in " + what);
	}

	private static final String TAG = "Performance";
}
