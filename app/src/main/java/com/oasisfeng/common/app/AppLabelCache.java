package com.oasisfeng.common.app;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import com.oasisfeng.island.util.Hacks;

import java.util.Locale;

/**
 * Cache for app labels.
 *
 * Created by Oasis on 2016/8/11.
 */
class AppLabelCache implements ComponentCallbacks {

	/** Get the cached label if valid. If not cached or invalid, trigger an asynchronous update. */
	@Nullable String get(final AppInfo info) {
		final String pkg = info.packageName;
		final int version = Hacks.ApplicationInfo_versionCode.get(info);	// 0 if hack is incompatible
		final String version_key = pkg + KEY_VERSION_CODE_SUFFIX;
		final int cached_version = mStore.getInt(version_key, -1);
		final String cached_label = mStore.getString(pkg, null);
		if (cached_version == version) return cached_label;		// Use cached label only if version is matched.
		// Load label asynchronously
		new AsyncTask<Void, Void, String>() {
			@Override protected String doInBackground(final Void... params) {
				final String label = info.loadLabel(mPackageManager).toString();
				return filterString(label);
			}

			private String filterString(final String name) {
				if (name == null) return null;
				StringBuilder buffer = null;
				for (int i = 0; i < name.length(); i ++) {
					final char c = name.charAt(i);
					if (c >= 0x20) {
						if (buffer != null) buffer.append(c);
						continue;
					}
					// Bad char found
					if (buffer == null) {
						buffer = new StringBuilder(name.length());
						buffer.append(name, 0, i);
					}
				}
				return buffer == null ? name : buffer.toString();
			}

			@Override protected void onPostExecute(final String label) {
				mStore.edit().putInt(version_key, version).putString(pkg, label).apply();
				if (label == null ? cached_label == null : label.equals(cached_label)) return;	// Unchanged
				mCallback.onLabelUpdate(pkg);
			}
		}.execute();
		return null;
	}

	@Override public void onConfigurationChanged(final Configuration config) {
		final Locale locale = config.locale;
		if (locale.equals(mLocale)) return;
		mLocale = locale;
		onLocaleChanged(locale.toLanguageTag());
	}

	private void onLocaleChanged(final String language_tag) {
		// Invalidate the whole cache
		mStore.edit().clear().putString(KEY_LANGUAGE_TAG, language_tag).apply();
	}

	@Override public void onLowMemory() {}

	interface Callback { void onLabelUpdate(String pkg); }

	AppLabelCache(final Context context, final Callback callback) {
		mStore = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		mCallback = callback;
		mPackageManager = context.getPackageManager();
		context.registerComponentCallbacks(this);		// No un-registration since AppLabelCache is never released.

		mLocale = context.getResources().getConfiguration().locale;
		final String language_tag = mLocale.toLanguageTag();
		final String cache_language_tag = mStore.getString(KEY_LANGUAGE_TAG, null);
		if (! language_tag.equals(cache_language_tag)) {
			onLocaleChanged(language_tag);
		}
	}

	private final SharedPreferences mStore;
	private final Callback mCallback;
	private final PackageManager mPackageManager;
	private Locale mLocale;

	private static final String PREFS_NAME = "app_label_cache";
	private static final String KEY_LANGUAGE_TAG = "_language_tag";
	private static final String KEY_VERSION_CODE_SUFFIX = /* package + */":ver";
}
