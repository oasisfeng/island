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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

/**
 * Provider for installed apps
 *
 * Created by Oasis on 2016/7/6.
 */
public abstract class AppListProvider<T extends AppInfo> extends ContentProvider {

	public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".apps";

	protected abstract T createEntry(final ApplicationInfo base, final T last);

//	public Iterable<T> allApps() {
//		return Collections.unmodifiableCollection(mAppMap.get().values());
//	}

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

	private void onPackageEvent(final String pkg) {
		final Map<String, T> apps = mAppMap.get();
		final T info = createEntryForPackage(pkg);
		if (info != null) {
			if (apps.put(pkg, info) != null) Log.i(TAG, "Updated: " + pkg);
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
			ApplicationInfo raw_info = null;
			try { //noinspection WrongConstant,deprecation,ConstantConditions
				raw_info = getContext().getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			} catch (final PackageManager.NameNotFoundException ignored) {}
			if (raw_info == null) {
				Log.w(TAG, "Unexpected package absence: " + pkg);
				continue;	// May happen during continuous events.
			}

			final T app = createEntry(raw_info, apps.get(pkg)/* last */);
			apps.put(pkg, app);
			Log.i(TAG, "Added: " + pkg);
		}

		notifyUpdate(pkgs);
	}

	private T createEntryForPackage(final String pkg) {
		try { //noinspection WrongConstant,deprecation,ConstantConditions
			final ApplicationInfo raw_info = getContext().getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
			final T last_info = mAppMap.get().get(pkg);
			return createEntry(raw_info, last_info);
		} catch (final PackageManager.NameNotFoundException ignored) { return null; }
	}

	private void notifyUpdate(final String[] pkgs) {
		mEventRegistry.notifyChange(pkgs);
//		mResolver.notifyChange(Uri.parse(CONTENT_URI_APP_PREFIX + pkg), null);
	}

	@Nullable @Override public Cursor query(final @NonNull Uri uri, final String[] projection, final String selection, final String[] selection_args, final String sort) {
		return null;
	}

	@Nullable @Override public String getType(final @NonNull Uri uri) {
		return "vnd.android.cursor.dir/vnd.com.oasisfeng.island.apps";
	}

	@Override public boolean onCreate() {
		Log.d(TAG, "Provider created.");

		mAppMap = Suppliers.memoize(() -> {
			final Context context = getContext();
			if (context == null) throw new IllegalStateException("Context is not ready");

			startStateMonitoring(context);

			final ConcurrentHashMap<String, T> app_map = new ConcurrentHashMap<>();
			//noinspection WrongConstant,deprecation
			final List<ApplicationInfo> apps = context.getPackageManager().getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
			for (final ApplicationInfo app : apps)
				app_map.put(app.packageName, createEntry(app, null));
			return app_map;
		});
//		mAppMap = Suppliers.memoize(() -> FluentIterable.from(mAppList.get()).uniqueIndex(info -> info.packageName));
		return true;
	}

	private void startStateMonitoring(final Context context) {
		mStateMonitoring = true;
		final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		context.registerReceiver(mPackageEventsObserver, filter);

		final IntentFilter pkgs_filter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		pkgs_filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		context.registerReceiver(mPackagesEventsObserver, pkgs_filter);
	}

	@Override public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (! mStateMonitoring) return;
		StreamSupport.stream(mAppMap.get().values()).forEach(AppInfo::onConfigurationChanged);
	}

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

//	private Context context() { return Preconditions.checkNotNull(getContext()); }

	@Override public @Nullable Uri insert(final @NonNull Uri uri, final ContentValues contentValues) { return null; }
	@Override public int delete(final @NonNull Uri uri, final String s, final String[] strings) { return 0; }
	@Override public int update(final @NonNull Uri uri, final ContentValues contentValues, final String s, final String[] strings) { return 0; }

	private Supplier<ConcurrentHashMap<String/* package */, T>> mAppMap;
	private final PackageEventRegistry mEventRegistry = new PackageEventRegistry();
	private boolean mStateMonitoring;

	private static final String TAG = "AppListProvider";

	private static class PackageEventRegistry extends CallbackRegistry<PackageChangeObserver, String[], Void> {

		private static final CallbackRegistry.NotifierCallback<PackageChangeObserver, String[], Void> NOTIFIER_CALLBACK = new CallbackRegistry.NotifierCallback<PackageChangeObserver, String[], Void>() {
			@Override public void onNotifyCallback(final PackageChangeObserver callback, final String[] pkgs, final int unused1, final Void unused2) {
				callback.onPackageEvent(pkgs);
			}
		};

		void notifyChange(final String[] pkgs) { notifyCallbacks(pkgs, 0, null); }

		PackageEventRegistry() { super(NOTIFIER_CALLBACK); }
	}
}
