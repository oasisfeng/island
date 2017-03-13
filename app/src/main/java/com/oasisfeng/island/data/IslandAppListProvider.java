package com.oasisfeng.island.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java8.util.function.Consumer;
import java8.util.function.Predicate;
import java8.util.stream.RefStreams;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Island-specific {@link AppListProvider}
 *
 * Created by Oasis on 2016/8/10.
 */
public class IslandAppListProvider extends AppListProvider<IslandAppInfo> {

	public static @NonNull IslandAppListProvider getInstance(final Context context) { return AppListProvider.getInstance(context); }
	public static @NonNull Predicate<IslandAppInfo> excludeSelf(final Context context) { return exclude(context.getPackageName()); }
	public static @NonNull Predicate<IslandAppInfo> exclude(final String pkg) { return app -> ! pkg.equals(app.packageName); }

	public @Nullable IslandAppInfo get(final String pkg, final UserHandle user) {
		if (Users.isOwner(user)) return super.get(pkg);
		if (! Users.isProfile(user)) return null;
		return mIslandAppMap.get().get(pkg);
	}

	public boolean isExclusive(final IslandAppInfo app) {
		if (GlobalStatus.profile == null) {
			if (! Users.isOwner(app.user)) throw new IllegalArgumentException("Known user: " + app);
			return true;	// No profile
		}
		final IslandAppInfo opposite = Users.isOwner(app.user) ? get(app.packageName, GlobalStatus.profile) : get(app.packageName);
		return opposite == null || ! opposite.isInstalled() || ! opposite.shouldShowAsEnabled();
	}

	@Override protected IslandAppInfo createEntry(final ApplicationInfo base, final IslandAppInfo last) {
		return new IslandAppInfo(this, GlobalStatus.current_user, base, last);
	}

	@Override protected void onAppLabelUpdate(final String pkg) {
		super.onAppLabelUpdate(pkg);
		// The implementation in super method only updates entries for apps in owner user, here we update entries for apps in Island.
		final IslandAppInfo entry = mIslandAppMap.get().get(pkg);
		if (entry == null) return;
		Log.d(TAG, "Label updated: " + pkg);
		final IslandAppInfo new_entry = new IslandAppInfo(this, GlobalStatus.profile, entry, null);
		mIslandAppMap.get().put(pkg, new_entry);

		notifyUpdate(Collections.singleton(new_entry));
	}

	@Override public Stream<IslandAppInfo> installedApps() {
		return RefStreams.concat(super.installedApps(), installedAppsInIsland());
	}

	private Stream<IslandAppInfo> installedAppsInIsland() {
		return StreamSupport.stream(mIslandAppMap.get().values());
	}

	private void onStartLoadingIslandApps(final ConcurrentHashMap<String/* package */, IslandAppInfo> apps) {
		Log.d(TAG, "Start loading apps...");
		mLauncherApps.get().registerCallback(mCallback);

		context().registerReceiver(new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			Log.i(TAG, "Profile added");
			final ConcurrentHashMap<String, IslandAppInfo> new_apps = mIslandAppMap.get();
			if (! new_apps.isEmpty()) {
				Log.e(TAG, "Non-empty app list when profile is created.");
				new_apps.clear();
			}
			refresh(new_apps);
			// Do not notify listeners, since this is a rare case. Client should take care of this by itself and reload all apps from provider.
		}}, new IntentFilter(Intent.ACTION_MANAGED_PROFILE_ADDED));

		context().registerReceiver(new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			Log.i(TAG, "Profile removed");
			mIslandAppMap.get().clear();
		}}, new IntentFilter(Intent.ACTION_MANAGED_PROFILE_REMOVED));

		refresh(apps);

		if (GlobalStatus.hasProfile()) mClonedHiddenSystemApps.get().initializeIfNeeded(context());
	}

	private void refresh(final Map<String, IslandAppInfo> apps) {
		if (GlobalStatus.profile != null) {		// Collect Island-specific apps
			if (! ShuttleContext.ALWAYS_USE_SHUTTLE && SDK_INT >= N && ! Hacks.LauncherApps_getApplicationInfo.isAbsent()) {    // Since Android N, we can query ApplicationInfo directly
				collectIslandApps_Api24(apps);
			} else if (! IslandManager.useServiceInProfile(mShuttleContext.get(), this::onIslandServiceConnected))
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

	/** Synchronous on Android 6+, asynchronous otherwise
	 *  @param callback will be invoked with the result, or null for failure (including {@link PackageManager.NameNotFoundException}. */
	private void queryApplicationInfoInProfile(final String pkg, final Consumer<ApplicationInfo> callback) {
		final UserHandle profile = GlobalStatus.profile;
		if (profile == null) {
			callback.accept(null);
			return;
		}
		if (! ShuttleContext.ALWAYS_USE_SHUTTLE && ! Hacks.IPackageManager_getApplicationInfo.isAbsent() && ! Hacks.ActivityThread_getPackageManager.isAbsent()
				&& ContextCompat.checkSelfPermission(context(), INTERACT_ACROSS_USERS) == PERMISSION_GRANTED) try {
			final ApplicationInfo info = Hacks.IPackageManager_getApplicationInfo.invoke(pkg, PM_FLAGS_GET_APP_INFO,
					Users.toId(profile)).on(Hacks.ActivityThread_getPackageManager.invoke().statically());
			callback.accept(info);
			return;
		} catch (final RemoteException ignored) {		// Package manager died, this should hardly happen.
			callback.accept(null);
			return;
		} catch (final SecurityException ignored) {}	// Fall-through. This should hardly happen as permission is checked.

		if (! ShuttleContext.ALWAYS_USE_SHUTTLE && SDK_INT >= N && ! Hacks.LauncherApps_getApplicationInfo.isAbsent()) {
			// Use MATCH_UNINSTALLED_PACKAGES to include frozen packages and then exclude non-installed packages with FLAG_INSTALLED.
			final ApplicationInfo info = Hacks.LauncherApps_getApplicationInfo.invoke(pkg, PM_FLAGS_GET_APP_INFO, GlobalStatus.profile).on(mLauncherApps.get());
			callback.accept(info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0 ? info : null);
		} else if (! IslandManager.useServiceInProfile(mShuttleContext.get(), service -> {
			final ApplicationInfo info = service.getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
			callback.accept(info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0 ? info : null);
		})) callback.accept(null);
	}

	public void refreshPackage(final String pkg, final @Nullable UserHandle user, final boolean add) {
		if (! Objects.equal(user, GlobalStatus.profile)) return;
		Log.d(TAG, "Update: " + pkg + (add ? " for pkg add" : " for pkg change"));
		queryApplicationInfoInProfile(pkg, info -> {
			if (info == null) return;
			final IslandAppInfo app = new IslandAppInfo(this, user, info, mIslandAppMap.get().get(pkg));
			if (add && app.isHidden()) {
				Log.w(TAG, "Correct the flag for unhidden package: " + pkg);
				app.setHidden(false);
			}
			mIslandAppMap.get().put(pkg, app);
			notifyUpdate(Collections.singleton(app));
		});
	}

	public boolean isHiddenSysAppCloned(final String pkg) {
		return mClonedHiddenSystemApps.get().isCloned(pkg);
	}

	public void setHiddenSysAppCloned(final String pkg) {
		if (GlobalStatus.hasProfile()) mClonedHiddenSystemApps.get().setCloned(pkg);
	}

	private final Supplier<ConcurrentHashMap<String/* package */, IslandAppInfo>> mIslandAppMap = Suppliers.memoize(() -> {
		final ConcurrentHashMap<String, IslandAppInfo> apps = new ConcurrentHashMap<>();
		onStartLoadingIslandApps(apps);
		return apps;
	});

	private final LauncherApps.Callback mCallback = new LauncherApps.Callback() {

		@Override public void onPackageRemoved(final String pkg, final UserHandle user) {
			if (! user.equals(GlobalStatus.profile)) return;
			final IslandAppInfo app = mIslandAppMap.get().get(pkg);
			if (app == null) {
				Log.e(TAG, "Removed package not found in Island: " + pkg);
				return;
			}
			if (app.isHidden()) return;		// The removal callback is triggered by freezing.
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
			refreshPackage(pkg, user, true);
		}

		@Override public void onPackageChanged(final String pkg, final UserHandle user) {
			refreshPackage(pkg, user, false);
		}

		@Override public void onPackagesAvailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesAvailable() is unsupported");
		}

		@Override public void onPackagesUnavailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesUnavailable() is unsupported");
		}
	};

	private final Supplier<ShuttleContext> mShuttleContext = Suppliers.memoize(() -> new ShuttleContext(context()));
	private final Supplier<LauncherApps> mLauncherApps = Suppliers.memoize(() -> (LauncherApps) context().getSystemService(Context.LAUNCHER_APPS_SERVICE));
	private final Supplier<ClonedHiddenSystemApps> mClonedHiddenSystemApps = Suppliers.memoize(
			() -> new ClonedHiddenSystemApps(context(), GlobalStatus.profile, pkg -> refreshPackage(pkg, GlobalStatus.profile, false)));

	private static final String TAG = "Island.AppListProv";
}
