package com.oasisfeng.island.analytics;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.Size;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.pattern.GlobalContextProvider;

import org.intellij.lang.annotations.Pattern;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstraction for analytics service
 *
 * Created by Oasis on 2016/5/26.
 */
@ParametersAreNonnullByDefault
public interface Analytics {

	interface Event {
		@CheckResult Event with(Param key, String value);
		void send();
	}

	enum Param {
		ITEM_ID(FirebaseAnalytics.Param.ITEM_ID),
		ITEM_NAME(FirebaseAnalytics.Param.ITEM_NAME),
		ITEM_CATEGORY(FirebaseAnalytics.Param.ITEM_CATEGORY),
		;
		Param(final @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String key) { this.key = key; }
		final String key;
	}

	@CheckResult Event event(@Size(min = 1, max = 40) @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") String event);
	void reportEvent(String event, Bundle params);
	void report(Throwable t);
	void setProperty(@Size(min = 1, max = 24) String key, @Size(max = 36) String value);
	default boolean setProperty(final String key, final boolean value) {
		setProperty(key, Boolean.toString(value));
		return value;
	}

	static Analytics $() { return Provider.getSingleton(); }

	class Provider {

		static Analytics getSingleton() {
			if (sSingleton == null) {
				final Context context = GlobalContextProvider.get();
				if (Users.isOwner()) {
					sSingleton = new AnalyticsImpl(context);
					AnalyticsProvider.enableIfNeeded(context);		// Ensure cross-user provider enabled if permission granted, BTW
				} else sSingleton = AnalyticsProvider.getClient(context);
			}
			return sSingleton;
		}

		private static Analytics sSingleton;
	}
}
