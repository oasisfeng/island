package com.oasisfeng.island.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.android.service.Services;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shuttle.ServiceShuttle;
import com.oasisfeng.island.util.Hacks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java8.util.function.Consumer;
import java8.util.function.Predicate;
import java8.util.stream.RefStreams;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Island-specific {@link AppListProvider}
 *
 * Created by Oasis on 2016/8/10.
 */
public class IslandAppListProvider extends AppListProvider<IslandAppInfo> {

	/** System packages shown to user always even if no launcher activities */
	public static final Collection<String> ALWAYS_VISIBLE_SYS_PKGS = Collections.singletonList("com.google.android.gms");
	public static final Predicate<IslandAppInfo> NON_SYSTEM = app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
	public static final Predicate<IslandAppInfo> NON_CRITICAL_SYSTEM = app -> ! SystemAppsManager.isCritical(app.packageName);

	public static IslandAppListProvider getInstance(final Context context) {
		return AppListProvider.getInstance(context);
	}

	public static Predicate<IslandAppInfo> excludeSelf(final Context context) {
		return exclude(context.getPackageName());
	}

	public static Predicate<IslandAppInfo> exclude(final String pkg) {
		return app -> ! pkg.equals(app.packageName);
	}

	@Override protected IslandAppInfo createEntry(final ApplicationInfo base, final IslandAppInfo last) {
		return new IslandAppInfo(this, GlobalStatus.current_user, base, last);
	}

	@Override public Stream<IslandAppInfo> installedApps() {
		return RefStreams.concat(super.installedApps(), installedAppsInIsland());
	}

	private Stream<IslandAppInfo> installedAppsInIsland() {
		return StreamSupport.stream(mIslandAppMap.get().values());
	}

	private void onStartLoadingIslandApps(final ConcurrentHashMap<String/* package */, IslandAppInfo> apps) {
		Log.d(TAG, "Start loading apps...");
		mLauncherApps.get().registerCallback(IslandAppListProvider.this.mCallback);
		final UserHandle profile = GlobalStatus.profile;

		context().registerReceiver(new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			final ConcurrentHashMap<String, IslandAppInfo> new_apps = mIslandAppMap.get();
			if (! new_apps.isEmpty()) {
				Log.e(TAG, "Non-empty app list when profile is created.");
				new_apps.clear();
			}
			onStartLoadingIslandApps(new_apps);
			// Do not notify listeners, since this is a rare case. Client should take care of this by itself and reload all apps from provider.
		}}, new IntentFilter(Intent.ACTION_MANAGED_PROFILE_ADDED));

		context().registerReceiver(new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			mIslandAppMap.get().clear();
		}}, new IntentFilter(Intent.ACTION_MANAGED_PROFILE_REMOVED));

		if (profile != null) {		// Collect Island-specific apps
			if (SDK_INT >= N && ! Hacks.LauncherApps_getApplicationInfo.isAbsent()) {    // Since Android N, we can query ApplicationInfo directly
				collectIslandApps_Api24(apps);
			} else if (! Services.bind(context(), IIslandManager.class, mServiceConnection))
				Log.w(TAG, "Failed to connect to Island");

//			// Get all apps with launcher activity in profile.
//			final List<LauncherActivityInfo> launchable_apps = mLauncherApps.get().getActivityList(null, profile);
//			String previous_pkg = null;
//			for (final LauncherActivityInfo app : launchable_apps) {
//				final ApplicationInfo app_info = app.getApplicationInfo();
//				final String pkg = app_info.packageName;
//				if (pkg.equals(previous_pkg)) continue;		// In case multiple launcher entries in one app.
//				apps.put(pkg, new IslandAppInfo(this, GlobalStatus.profile, app_info, null));
//				previous_pkg = pkg;
//			}
		}
	}

	private void collectIslandApps_Api24(final Map<String, IslandAppInfo> apps) {
		super.installedApps().map(app -> Hacks.LauncherApps_getApplicationInfo.invoke(app.packageName, PM_FLAGS_GET_APP_INFO, GlobalStatus.profile).on(mLauncherApps.get()))
				.filter(info -> info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0)
				.forEach(info -> apps.put(info.packageName, new IslandAppInfo(this, GlobalStatus.profile, info, null)));
	}

	private void onIslandServiceConnected(final IIslandManager island) {
		final List<ApplicationInfo> apps; try {
			apps = island.queryApps(PM_FLAGS_GET_APP_INFO, ApplicationInfo.FLAG_INSTALLED);
		} catch (final RemoteException e) {
			Log.e(TAG, "Unexpected remote error", e);
			return;
		}
		final List<IslandAppInfo> updated = new ArrayList<>(apps.size());
		final ConcurrentHashMap<String, IslandAppInfo> app_map = mIslandAppMap.get();
		for (final ApplicationInfo app : apps) {
			final IslandAppInfo info = new IslandAppInfo(this, GlobalStatus.profile, app, null);
			app_map.put(app.packageName, info);
			updated.add(info);
		}
		notifyUpdate(updated);
	}

	/** Synchronous on Android 7+, asynchronous otherwise */
	private void queryApplicationInfoInProfile(final String pkg, final Consumer<ApplicationInfo> callback) {
		if (SDK_INT >= N && ! Hacks.LauncherApps_getApplicationInfo.isAbsent()) {
			// Use MATCH_UNINSTALLED_PACKAGES to include frozen packages and then exclude non-installed packages with FLAG_INSTALLED.
			final ApplicationInfo info = Hacks.LauncherApps_getApplicationInfo.invoke(pkg, PM_FLAGS_GET_APP_INFO, GlobalStatus.profile).on(mLauncherApps.get());
			callback.accept(info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0 ? info : null);
		} else if (! Services.use(context(), IIslandManager.class, IIslandManager.Stub::asInterface, service -> {
			final ApplicationInfo info = service.getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
			callback.accept(info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0 ? info : null);
		})) callback.accept(null);
	}

	private final Supplier<ConcurrentHashMap<String/* package */, IslandAppInfo>> mIslandAppMap = Suppliers.memoize(() -> {
		final ConcurrentHashMap<String, IslandAppInfo> apps = new ConcurrentHashMap<>();
		onStartLoadingIslandApps(apps);
		return apps;
	});

	private final LauncherApps.Callback mCallback = new LauncherApps.Callback() {

		@Override public void onPackageRemoved(final String pkg, final UserHandle user) {
			if (! user.equals(GlobalStatus.profile)) return;
			if (mIslandAppMap.get().get(pkg).isHidden()) return;	// The removal callback is triggered by freezing.
			queryApplicationInfoInProfile(pkg, info -> {
				if (info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {	// Frozen
					final IslandAppInfo new_info = new IslandAppInfo(IslandAppListProvider.this, user, info, mIslandAppMap.get().get(pkg));
					if (! new_info.isHidden()) {
						Log.w(TAG, "Correct the flag for hidden package: " + pkg);
						new_info.setHidden(true);
					}
					mIslandAppMap.get().put(pkg, new_info);
					notifyUpdate(Collections.singleton(new_info));
				} else {	// Uninstalled in profile
					final IslandAppInfo removed_app = mIslandAppMap.get().remove(pkg);
					if (removed_app != null) notifyRemoval(Collections.singleton(removed_app));
				}
			});
		}

		@Override public void onPackageAdded(final String pkg, final UserHandle user) {
			updatePackage(pkg, user, true);
		}

		@Override public void onPackageChanged(final String pkg, final UserHandle user) {
			updatePackage(pkg, user, false);
		}

		@Override public void onPackagesAvailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesAvailable() is unsupported");
		}

		@Override public void onPackagesUnavailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesUnavailable() is unsupported");
		}

		private void updatePackage(final String pkg, final UserHandle user, final boolean add) {
			if (! user.equals(GlobalStatus.profile)) return;
			Log.d(TAG, "Update: " + pkg + (add ? " for pkg add" : " for pkg change"));
			queryApplicationInfoInProfile(pkg, info -> {
				if (info == null) return;
				final IslandAppInfo app = new IslandAppInfo(IslandAppListProvider.this, user, info, mIslandAppMap.get().get(pkg));
				if (add && app.isHidden()) {
					Log.w(TAG, "Correct the flag for unhidden package: " + pkg);
					app.setHidden(false);
				}
				mIslandAppMap.get().put(pkg, app);
				notifyUpdate(Collections.singleton(app));
			});
		}
	};

	private final ServiceConnection mServiceConnection = new ServiceShuttle.ShuttleServiceConnection() {

		@Override public void onServiceConnected(final IBinder service) {
			onIslandServiceConnected(IIslandManager.Stub.asInterface(service));
			context().unbindService(this);
		}

		@Override public void onServiceDisconnected() {}
	};

	private final Supplier<LauncherApps> mLauncherApps = Suppliers.memoize(() -> (LauncherApps) context().getSystemService(Context.LAUNCHER_APPS_SERVICE));

	private static final String TAG = "Island.AppsProv";
}
