package com.oasisfeng.island.analytics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.oasisfeng.island.shared.BuildConfig;

import org.intellij.lang.annotations.Pattern;

/**
 * Abstraction for analytics service
 *
 * Created by Oasis on 2016/5/26.
 */

public class Analytics extends ContentProvider {

	public void setProperty(final String key, final String value) {
		mAnalytics.get().setUserProperty(key, value);
	}

	public boolean setProperty(final String key, final boolean value) {
		setProperty(key, Boolean.toString(value));
		return value;
	}

	public interface Event {
		@CheckResult Event with(Param key, String value);
		void send();
	}

	public @CheckResult Event event(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String event) {
		final Bundle bundle = new Bundle();
		return new Event() {

			@Override public @CheckResult Event with(final Param key, final String value) {
				bundle.putString(key.key, value);
				return this;
			}

			@Override public void send() {
				reportEventInternal(event, bundle.isEmpty() ? null : bundle);
			}
		};
	}

	public void report(final Throwable t) {
		if (BuildConfig.DEBUG) Log.e(TAG, "About to report", t);
		FirebaseCrash.report(t);
		// TODO: Verify the reach rate of the following redundant reporting via event.
		final Bundle bundle = new Bundle(); bundle.putString(FirebaseAnalytics.Param.LOCATION, t.getMessage());
		reportEventInternal("temp_error", bundle);
	}

	private synchronized Analytics reportEventInternal(final @NonNull String event, final @Nullable Bundle params) {
		Log.d(TAG, "Event [" + event + "]: " + params);
		mAnalytics.get().logEvent(event, params);
		return this;
	}

	public static Analytics $() {
		return sSingleton;
	}

	@Override public boolean onCreate() {
		sSingleton = this;
		return true;
	}

	@Nullable @Override public Uri insert(final @NonNull Uri uri, final ContentValues values) {
		return null;	// TODO
	}
	@Override public @Nullable Cursor query(final @NonNull Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) { return null; }
	@Override public @Nullable String getType(final @NonNull Uri uri) { return null; }
	@Override public int delete(final @NonNull Uri uri, final String selection, final String[] selectionArgs) { return 0; }
	@Override public int update(final @NonNull Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) { return 0; }

	private static Analytics sSingleton;
	private static final String TAG = "Analytics";

	@SuppressWarnings("ConstantConditions") private final Supplier<FirebaseAnalytics> mAnalytics = Suppliers.memoize(() -> FirebaseAnalytics.getInstance(getContext()));

	public enum Param {
		ITEM_ID(FirebaseAnalytics.Param.ITEM_ID),
		ITEM_CATEGORY(FirebaseAnalytics.Param.ITEM_CATEGORY);

		Param(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String key) { this.key = key; }
		final String key;
	}
}
