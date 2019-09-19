package com.oasisfeng.common.app;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.util.Log;

import com.oasisfeng.island.util.Hacks;

import java.util.Objects;

import androidx.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Cache for app labels.
 *
 * Created by Oasis on 2016/8/11.
 */
class AppLabelCache implements ComponentCallbacks {

	private static final boolean CLEAR_CACHE_UPON_START = Boolean.FALSE;

	/** Get the cached label if valid. If not cached or invalid, trigger an asynchronous update. */
	@Nullable String get(final AppInfo info) {
		if (info.nonLocalizedLabel != null) return filterString(info.nonLocalizedLabel.toString());

		final String pkg = info.packageName;
		final int version = Hacks.ApplicationInfo_versionCode.get(info);	// 0 if hack is incompatible
		final String version_key = pkg + KEY_VERSION_CODE_SUFFIX;
		final int cached_version = mStore.getInt(version_key, -1);
		final String cached_label = mStore.getString(pkg, null);
		if (cached_version == version) return cached_label;		// Use cached label only if version is matched.

		// Load label asynchronously
		Log.v(TAG, (cached_version == -1 ? "Load: " : "Reload: ") + info.packageName);
		@SuppressWarnings("unused") @SuppressLint("StaticFieldLeak") final AsyncTask unused = new AsyncTask<Void, Void, String>() {
			@Override protected String doInBackground(final Void... params) {
				return filterString(info.loadLabel(mPackageManager).toString());
			}

			@Override protected void onPostExecute(final String label) {
				mStore.edit().putInt(version_key, version).putString(pkg, label).apply();
				if (Objects.equals(label, cached_label)) return;	// Unchanged
				Log.v(TAG, "Loaded: " + info.packageName + " = " + label);
				mCallback.onLabelUpdate(pkg);
			}
		}.executeOnExecutor(AppInfo.TASK_THREAD_POOL);
		return null;
	}

	private static String filterString(final String name) {
		if (name == null) return null;
		StringBuilder buffer = null;
		for (int i = 0; i < name.length(); i ++) {
			final char c = name.charAt(i);
			if (c < 0x20) {		// Bad char found
				if (buffer == null) buffer = new StringBuilder(name.length()).append(name, 0, i);
			} else if (buffer != null) buffer.append(c);
		}
		return buffer == null ? name : buffer.toString();
	}

	@Override public void onConfigurationChanged(final Configuration config) {
		final String language_tags;
		if (SDK_INT < N) language_tags = config.locale.toLanguageTag();
		else language_tags = config.getLocales().toLanguageTags();

		final String cache_language_tags = mStore.getString(KEY_LANGUAGE_TAGS, null);
		if (language_tags.equals(cache_language_tags)) return;
		mStore.edit().clear().putString(KEY_LANGUAGE_TAGS, language_tags).apply();	// Invalidate the whole cache
	}

	@Override public void onLowMemory() {}

	interface Callback { void onLabelUpdate(String pkg); }

	AppLabelCache(final Context context, final Callback callback) {
		mStore = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		if (CLEAR_CACHE_UPON_START) mStore.edit().clear().apply();
		mCallback = callback;
		mPackageManager = context.getPackageManager();
		context.registerComponentCallbacks(this);		// No un-registration since AppLabelCache is never released.
		onConfigurationChanged(context.getResources().getConfiguration());
	}

	private final SharedPreferences mStore;
	private final Callback mCallback;
	private final PackageManager mPackageManager;

	private static final String PREFS_NAME = "app_label_cache";
	private static final String KEY_LANGUAGE_TAGS = "_language_tags";
	private static final String KEY_VERSION_CODE_SUFFIX = /* package + */":ver";
	private static final String TAG = "AppLabelCache";
}
