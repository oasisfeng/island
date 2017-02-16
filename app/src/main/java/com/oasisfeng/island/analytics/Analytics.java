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
import com.oasisfeng.island.util.Users;

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
		mAnalytics.get().logEvent(event, params);
		return this;
	}

	public static Analytics $() {
		return sSingleton;
	}

	@Override public boolean onCreate() {
		if (Users.isOwner()) //noinspection ConstantConditions
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
}
