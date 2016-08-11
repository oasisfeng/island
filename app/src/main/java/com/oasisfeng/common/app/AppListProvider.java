package com.oasisfeng.common.app;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.databinding.CallbackRegistry;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.island.BuildConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

/**
 * Provider for installed apps
 *
 * Created by Oasis on 2016/7/6.
 */
public abstract class AppListProvider<T extends AppInfo> extends ContentProvider {

	private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".apps";

	/** The implementation should be as fast as possible, since it may be called in mass. */
	protected abstract T createEntry(final ApplicationInfo base, final T last);

	public Stream<T> installedApps() {
		return StreamSupport.stream(mAppMap.get().values());
	}

	public Map<String, T> map() {
		return Collections.unmodifiableMap(mAppMap.get());
	}

	protected static <T extends AppListProvider> T getInstance(final Context context) {
		final ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(AUTHORITY);
		if (client == null) throw new IllegalStateException("AppListProvider not associated with authority: " + AUTHORITY);
		try {
			final ContentProvider provider = client.getLocalContentProvider();
			if (provider == null)
				throw new IllegalStateException("android:multiprocess=\"true\" is required for this provider.");
			if (! (provider instanceof AppListProvider)) throw new IllegalArgumentException("");
			@SuppressWarnings("unchecked") final T casted = (T) provider;
			return casted;
		} finally { //noinspection deprecation
			client.release();
		}
	}

	public interface PackageChangeObserver {
		/** Called when package event happens the info argument might be null if package is removed. */
		void onPackageEvent(String[] pkgs);
	}

	public void registerObserver(final PackageChangeObserver observer) { mEventRegistry.add(observer); }
	public void unregisterObserver(final PackageChangeObserver observer) { mEventRegistry.remove(observer); }

	/** Called upon the first fetch */
	private ConcurrentHashMap<String/* package */, T> startAndLoadApps() {
		mStarted = true;
		final IntentFilter pkg_filter = new IntentFilter();
		pkg_filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		pkg_filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		pkg_filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		pkg_filter.addDataScheme("package");
		context().registerReceiver(mPackageEventsObserver, pkg_filter);

		final IntentFilter pkgs_filter = new IntentFilter();
		pkgs_filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		pkgs_filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		context().registerReceiver(mPackagesEventsObserver, pkgs_filter);

		final ConcurrentHashMap<String, T> apps = new ConcurrentHashMap<>();
		//noinspection WrongConstant
		for (final ApplicationInfo app : context().getPackageManager().getInstalledApplications(PM_FLAGS_GET_APP_INFO))
			apps.put(app.packageName, createEntry(app, null));
		return apps;
	}

	private void onPackageEvent(final String pkg) {
		final Map<String, T> apps = mAppMap.get();
		T entry = null;
		try { //noinspection WrongConstant
			final ApplicationInfo info = context().getPackageManager().getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
			final T last_entry = mAppMap.get().get(pkg);
			entry = createEntry(info, last_entry);
		} catch (final PackageManager.NameNotFoundException ignored) {}
		if (entry != null) {
			if (apps.put(pkg, entry) != null) Log.i(TAG, "Updated: " + pkg);
			else Log.i(TAG, "Added: " + pkg);
		} else if (apps.remove(pkg) != null)
			Log.i(TAG, "Removed: " + pkg);
		else /* if nothing to remove */ return;		// Already removed somewhere before?

		notifyUpdate(new String[] { pkg });
	}

	// Eventual consistency strategy to improve performance
	private void onPackagesEvent(final String[] pkgs, final boolean removed) {
		final Map<String, T> apps = mAppMap.get();
		if (removed) {
			for (final String pkg : pkgs) apps.remove(pkg);
			Log.i(TAG, "Removed: " + Arrays.toString(pkgs));
		} else for (final String pkg : pkgs) {
			ApplicationInfo info = null;
			try { //noinspection WrongConstant
				info = context().getPackageManager().getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
			} catch (final PackageManager.NameNotFoundException ignored) {}
			if (info == null) {
				Log.w(TAG, "Unexpected package absence: " + pkg);
				continue;	// May happen during continuous events.
			}

			final T app = createEntry(info, apps.get(pkg)/* last entry */);
			apps.put(pkg, app);
			Log.i(TAG, "Added: " + pkg);
		}

		notifyUpdate(pkgs);
	}

	/** Called by {@link AppLabelCache} when app label is lazily-loaded or updated (changed from cache) */
	private void onAppLabelUpdate(final String pkg) {
		final T entry = mAppMap.get().get(pkg);
		if (entry == null) return;
		final T new_entry = createEntry(entry, null);
		mAppMap.get().put(pkg, new_entry);

		notifyUpdate(new String[] { pkg });
	}

	@Override public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (! mStarted) return;
		for (final Map.Entry<String, T> entry : mAppMap.get().entrySet())
			entry.setValue(createEntry(entry.getValue(), null));

		final Set<String> pkgs = mAppMap.get().keySet();
		notifyUpdate(pkgs.toArray(new String[pkgs.size()]));		// TODO: Better solution for performance?
	}

	String getCachedOrTempLabel(final AppInfo info) {
		final String cached = mAppLabelCache.get().get(info);
		if (cached != null) return cached;
		return info.packageName;		// As temporary label
	}

	private void notifyUpdate(final String[] pkgs) { mEventRegistry.notifyCallbacks(pkgs, 0, null); }

	private final BroadcastReceiver mPackageEventsObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData();
		if (data == null) return;
		final String pkg = data.getSchemeSpecificPart();
		if (pkg == null) return;
		final boolean is_changed = Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction());
		if (is_changed) {		// Skip non-package-level (components) change
			final String[] changed_components = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST);
			if (changed_components == null || changed_components.length != 1 || ! pkg.equals(changed_components[0])) return;
		}
		onPackageEvent(pkg);
	}};

	private final BroadcastReceiver mPackagesEventsObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final String[] pkgs = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
		if (pkgs == null || pkgs.length == 0) return;
		onPackagesEvent(pkgs, Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(intent.getAction()));
	}};

	@Override public void onTrimMemory(final int level) {
		if (! mStarted) return;
		final ConcurrentHashMap<String, T> apps = mAppMap.get();
		switch (level) {
		case TRIM_MEMORY_RUNNING_MODERATE:
		case TRIM_MEMORY_RUNNING_LOW:
		case TRIM_MEMORY_RUNNING_CRITICAL:
		case TRIM_MEMORY_UI_HIDDEN:
		case TRIM_MEMORY_BACKGROUND:
			Log.i(TAG, "Trim memory for level " + level);
			StreamSupport.stream(apps.values()).forEach(AppInfo::trimMemoryOnUiHidden);
		case TRIM_MEMORY_MODERATE:
		case TRIM_MEMORY_COMPLETE:
			Log.i(TAG, "Clean memory for level " + level);
			StreamSupport.stream(apps.values()).forEach(AppInfo::trimMemoryOnCritical);
		}
	}

	@Override public boolean onCreate() {
		Log.d(TAG, "Provider created.");
		return true;
	}

	private Context context() { return getContext(); }

	/* The normal ContentProvider IPC interface is not used. */
	@Nullable @Override public Cursor query(final @NonNull Uri uri, final String[] projection, final String selection, final String[] selection_args, final String sort) { return null; }
	@Nullable @Override public String getType(final @NonNull Uri uri) { return "vnd.android.cursor.dir/vnd.com.oasisfeng.island.apps"; }
	@Override public @Nullable Uri insert(final @NonNull Uri uri, final ContentValues contentValues) { return null; }
	@Override public int delete(final @NonNull Uri uri, final String s, final String[] strings) { return 0; }
	@Override public int update(final @NonNull Uri uri, final ContentValues contentValues, final String s, final String[] strings) { return 0; }

	/** This provider is lazily started to its full working state. */
	private boolean mStarted;
	private final Supplier<ConcurrentHashMap<String/* package */, T>> mAppMap = Suppliers.memoize(this::startAndLoadApps);
	private final CallbackRegistry<PackageChangeObserver, String[], Void> mEventRegistry = new CallbackRegistry<>(new CallbackRegistry.NotifierCallback<PackageChangeObserver, String[], Void>() {
		@Override public void onNotifyCallback(final PackageChangeObserver callback, final String[] pkgs, final int unused1, final Void unused2) {
			callback.onPackageEvent(pkgs);
		}
	});
	private final Supplier<AppLabelCache> mAppLabelCache = Suppliers.memoize(() -> new AppLabelCache(context(), this::onAppLabelUpdate));

	@SuppressWarnings("deprecation") private static final int PM_FLAGS_GET_APP_INFO = PackageManager.GET_UNINSTALLED_PACKAGES
																					| PackageManager.GET_DISABLED_COMPONENTS
												   									| PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS;
	private static final String TAG = "AppListProvider";
}
