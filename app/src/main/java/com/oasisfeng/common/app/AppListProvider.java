package com.oasisfeng.common.app;

import android.annotation.SuppressLint;
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
import android.net.Uri;
import android.util.Log;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.CallbackRegistry;
import java9.util.stream.Stream;
import java9.util.stream.StreamSupport;

/**
 * Provider for installed apps
 *
 * Created by Oasis on 2016/7/6.
 */
public abstract class AppListProvider<T extends AppInfo> extends ContentProvider {

	private static final String AUTHORITY_SUFFIX = ".apps";

	/** The implementation should be as fast as possible, since it may be called in mass. */
	protected abstract T createEntry(final ApplicationInfo base, final T last);

	protected static @NonNull <T extends AppListProvider> T getInstance(final Context context) {
		final String authority = context.getPackageName() + AUTHORITY_SUFFIX;		// Do not use BuildConfig.APPLICATION_ID
		final ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(authority);
		if (client == null) throw new IllegalStateException("AppListProvider not associated with authority: " + authority);
		try {	// DO NOT replace this with try-with-resources, since ContentProviderClient.close() was added in API 24.
			final ContentProvider provider = client.getLocalContentProvider();
			if (provider == null)
				throw new IllegalStateException("android:multiprocess=\"true\" is required for this provider.");
			if (! (provider instanceof AppListProvider)) throw new IllegalArgumentException("");
			@SuppressWarnings("unchecked") final T casted = (T) provider;
			return casted;
		} finally {
			client.release();
		}
	}

	public Stream<T> installedApps() { return StreamSupport.stream(mAppMap.get().values()); }

	public T get(final String pkg) { return mAppMap.get().get(pkg); }

	public interface PackageChangeObserver<T extends AppInfo> {
		/** Called when package event happens the info argument might be null if package is removed. */
		void onPackageUpdate(Collection<T> apps);
		void onPackageRemoved(Collection<T> apps);
	}

	public void registerObserver(final PackageChangeObserver<T> observer) { mEventRegistry.add(observer); }
	public void unregisterObserver(final PackageChangeObserver<T> observer) { mEventRegistry.remove(observer); }

	/** Called upon the first fetch to build the list lazily and start to monitor related events. */
	// TODO: onStop() when offloading the while list
	@CallSuper protected void onStartLoadingApps(final Map<String/* package */, T> apps) {
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

		//noinspection WrongConstant
		for (final ApplicationInfo app : context().getPackageManager().getInstalledApplications(PM_FLAGS_APP_INFO))
			apps.put(app.packageName, createEntry(app, null));
	}

	private void onPackageEvent(final String pkg) {
		final Map<String, T> apps = mAppMap.get();
		T entry = null;
		try { //noinspection WrongConstant
			final ApplicationInfo info = context().getPackageManager().getApplicationInfo(pkg, PM_FLAGS_APP_INFO);
			final T last_entry = mAppMap.get().get(pkg);
			entry = createEntry(info, last_entry);
		} catch (final PackageManager.NameNotFoundException ignored) {}

		if (entry != null) {
			if (apps.put(pkg, entry) != null) Log.i(TAG, "Updated: " + pkg);
			else Log.i(TAG, "Added: " + pkg);
			notifyUpdate(Collections.singleton(entry));
		} else if ((entry = apps.remove(pkg)) != null) {
			Log.i(TAG, "Removed: " + pkg);
			notifyRemoval(Collections.singleton(entry));
		} else Log.e(TAG, "Event of non-existent package: " + pkg);		// Already removed somewhere before?
	}

	// Eventual consistency strategy to improve performance
	private void onPackagesEvent(final String[] pkgs, final boolean removed) {
		final Map<String, T> apps = mAppMap.get();
		if (removed) {
			final List<T> removed_apps = new ArrayList<>();
			for (final String pkg : pkgs) {
				final T removed_app = apps.remove(pkg);
				if (removed_app != null) removed_apps.add(removed_app);
			}
			Log.i(TAG, "Removed: " + Arrays.toString(pkgs));
			notifyRemoval(removed_apps);
		} else {
			final List<T> updated_apps = new ArrayList<>();
			for (final String pkg : pkgs) {
				ApplicationInfo info = null;
				try { //noinspection WrongConstant
					info = context().getPackageManager().getApplicationInfo(pkg, PM_FLAGS_APP_INFO);
				} catch (final PackageManager.NameNotFoundException ignored) {}
				if (info == null) {
					Log.w(TAG, "Unexpected package absence: " + pkg);
					continue;	// May happen during continuous events.
				}

				final T app = createEntry(info, apps.get(pkg)/* last entry */);
				apps.put(pkg, app);
				updated_apps.add(app);
				Log.i(TAG, "Added: " + pkg);
			}
			notifyUpdate(updated_apps);
		}
	}

	/** Called by {@link AppLabelCache} when app label is lazily-loaded or updated (changed from cache) */
	protected void onAppLabelUpdate(final String pkg) {
		final T entry = mAppMap.get().get(pkg);
		if (entry == null) return;
		Log.d(TAG, "Label updated: " + pkg);
		final T new_entry = createEntry(entry, entry);	// In createEntry(), label is reloaded from the label cache.
		mAppMap.get().put(pkg, new_entry);

		notifyUpdate(Collections.singleton(new_entry));
	}

	@Override public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (! mStarted) return;
		for (final Map.Entry<String, T> entry : mAppMap.get().entrySet())
			entry.setValue(createEntry(entry.getValue(), null));

		final Collection<T> pkgs = mAppMap.get().values();
		notifyUpdate(Collections.unmodifiableCollection(pkgs));
	}

	String getCachedOrTempLabel(final AppInfo info) {
		final String cached = mAppLabelCache.get().get(info);
		if (cached != null) return cached;
		return info.nonLocalizedLabel != null ? info.nonLocalizedLabel.toString() : info.packageName;	// As temporary label
	}

	protected void notifyUpdate(final Collection<T> apps) { mEventRegistry.notifyCallbacks(apps, CALLBACK_UPDATE, null); }
	protected void notifyRemoval(final Collection<T> apps) { mEventRegistry.notifyCallbacks(apps, CALLBACK_REMOVE, null); }

	private final BroadcastReceiver mPackageEventsObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData();
		if (data == null) return;
		final String pkg = data.getSchemeSpecificPart();
		if (pkg == null) return;
		if (Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())) {
			final String[] changed_components = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST);
			if (changed_components == null || changed_components.length != 1 || ! pkg.equals(changed_components[0]))
				return;		// Skip component-level changes, we only care about package-level changes.
		} else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
			return;			// Skip the package removal broadcast if package is being replaced. ACTION_PACKAGE_ADDED will arrive soon.
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
			Log.d(TAG, "Trim memory for level " + level);
			StreamSupport.stream(apps.values()).forEach(AppInfo::trimMemoryOnUiHidden);
			break;
		case TRIM_MEMORY_MODERATE:
		case TRIM_MEMORY_COMPLETE:
			Log.i(TAG, "Clean memory for level " + level);
			StreamSupport.stream(apps.values()).forEach(AppInfo::trimMemoryOnCritical);
			break;
		}
	}

	@Override public boolean onCreate() {
		Log.d(TAG, "Provider created.");
		return true;
	}

	protected Context context() { return getContext(); }

	/* The normal ContentProvider IPC interface is not used. */
	@Nullable @Override public Cursor query(final @NonNull Uri uri, final String[] projection, final String selection, final String[] selection_args, final String sort) { return null; }
	@Nullable @Override public String getType(final @NonNull Uri uri) { return "vnd.android.cursor.dir/vnd.com.oasisfeng.island.apps"; }
	@Override public @Nullable Uri insert(final @NonNull Uri uri, final ContentValues contentValues) { return null; }
	@Override public int delete(final @NonNull Uri uri, final String s, final String[] strings) { return 0; }
	@Override public int update(final @NonNull Uri uri, final ContentValues contentValues, final String s, final String[] strings) { return 0; }

	/** This provider is lazily started to its full working state. */
	private boolean mStarted;
	private final Supplier<ConcurrentHashMap<String/* package */, T>> mAppMap = Suppliers.memoize(() -> {
		ConcurrentHashMap<String, T> apps = new ConcurrentHashMap<>();
		onStartLoadingApps(apps);
		return apps;
	});
	private final CallbackRegistry<PackageChangeObserver<T>, Collection<T>, Void> mEventRegistry = new CallbackRegistry<>(new CallbackRegistry.NotifierCallback<PackageChangeObserver<T>, Collection<T>, Void>() {
		@Override public void onNotifyCallback(final PackageChangeObserver<T> callback, final Collection<T> apps, final int callback_index, final Void unused2) {
			if (callback_index == CALLBACK_UPDATE) callback.onPackageUpdate(apps);
			else if (callback_index == CALLBACK_REMOVE) callback.onPackageRemoved(apps);
		}
	});
	private final Supplier<AppLabelCache> mAppLabelCache = Suppliers.memoize(() -> new AppLabelCache(context(), this::onAppLabelUpdate));

	private static final int CALLBACK_UPDATE = 0;
	private static final int CALLBACK_REMOVE = -1;
	@SuppressLint("InlinedApi") protected static final int PM_FLAGS_APP_INFO
			= PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;
	private static final String TAG = "AppListProvider";
}
