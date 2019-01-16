package com.oasisfeng.island.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.provisioning.CriticalAppsManager;
import com.oasisfeng.island.provisioning.SystemAppsManager;
import com.oasisfeng.island.shuttle.ContextShuttle;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.shuttle.ServiceShuttleContext;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import java9.util.function.Consumer;
import java9.util.function.Predicate;
import java9.util.stream.Collectors;
import java9.util.stream.Stream;
import java9.util.stream.StreamSupport;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

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
		final boolean app_in_owner_user = Users.isOwner(app.user);
		if (app_in_owner_user && Users.profile == null) return true;
		final IslandAppInfo opposite = app_in_owner_user ? get(app.packageName, Users.profile) : get(app.packageName);
		return opposite == null || ! opposite.isInstalled() || ! opposite.shouldShowAsEnabled();
	}

	@Override protected IslandAppInfo createEntry(final ApplicationInfo base, final IslandAppInfo last) {
		return new IslandAppInfo(this, Users.current(), base, last);
	}

	@Override protected void onAppLabelUpdate(final String pkg) {
		super.onAppLabelUpdate(pkg);
		// The implementation in super method only updates entries for apps in owner user, here we update entries for apps in Island.
		final IslandAppInfo entry = mIslandAppMap.get().get(pkg);
		if (entry == null) return;
		Log.d(TAG, "Label updated: " + pkg);
		final IslandAppInfo new_entry = new IslandAppInfo(this, Users.profile, entry, null);
		mIslandAppMap.get().put(pkg, new_entry);

		notifyUpdate(Collections.singleton(new_entry));
	}

	@Override public Stream<IslandAppInfo> installedApps() {
		return Stream.concat(super.installedApps(), installedAppsInIsland());
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

		if (Users.hasProfile()) mClonedHiddenSystemApps.get().initializeIfNeeded(context());
	}

	private void refresh(final Map<String, IslandAppInfo> output_apps) {
		if (Users.profile != null) {		// Collect Island-specific apps
			if (! ServiceShuttleContext.ALWAYS_USE_SHUTTLE && SDK_INT >= N && Hacks.LauncherApps_getApplicationInfo != null) {    // Since Android N, we can query ApplicationInfo directly
				super.installedApps().map(app -> getApplicationInfo(app.packageName, Users.profile))
						.filter(info -> info != null && (info.flags & FLAG_INSTALLED) != 0)
						.forEach(info -> output_apps.put(info.packageName, new IslandAppInfo(this, Users.profile, info, null)));
				Log.d(TAG, "All apps loaded.");
			} else {
				final Context context = context();
				// TODO: ParceledListSlice to avoid TransactionTooLargeException.
				MethodShuttle.runInProfile(context,	() -> StreamSupport.stream(context.getPackageManager().getInstalledApplications(PM_FLAGS_GET_APP_INFO))
						.filter(app -> (app.flags & FLAG_INSTALLED) != 0).collect(Collectors.toList())).whenComplete((apps, e) -> {
					if (e != null) {
						Log.w(TAG, "Failed to query apps in Island", e);
						return;
					}
					Log.v(TAG, "Connected to profile.");
					final List<IslandAppInfo> updated = new ArrayList<>(apps.size());
					final ConcurrentHashMap<String, IslandAppInfo> app_map = mIslandAppMap.get();
					for (final ApplicationInfo app : apps) {
						final IslandAppInfo info = new IslandAppInfo(this, Users.profile, app, null);
						app_map.put(app.packageName, info);
						updated.add(info);
					}
					Log.d(TAG, "All apps loaded.");
					notifyUpdate(updated);
				});
			}
		}
	}

	/** @param callback will be invoked with the result, or null for failure (including {@link PackageManager.NameNotFoundException}. */
	private void queryApplicationInfoInProfile(final String pkg, final Consumer<ApplicationInfo> callback) {
		final UserHandle profile = Users.profile;
		if (profile == null) {
			callback.accept(null);
			return;
		}
		final Context context = context();
		if (! ServiceShuttleContext.ALWAYS_USE_SHUTTLE && Permissions.has(context, Permissions.INTERACT_ACROSS_USERS)) try {
			final ApplicationInfo info = mProfilePackageManager.get().getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
			callback.accept(info);
			return;
		} catch (final PackageManager.NameNotFoundException ignored) {
			callback.accept(null);
			return;
		} catch (final SecurityException ignored) {}	// Fall-through. This should hardly happen as permission is checked.

		final List<LauncherActivityInfo> activities;
		if (! ServiceShuttleContext.ALWAYS_USE_SHUTTLE && SDK_INT >= N && Hacks.LauncherApps_getApplicationInfo != null) {
			// Use MATCH_UNINSTALLED_PACKAGES to include frozen packages and then exclude non-installed packages with FLAG_INSTALLED.
			final ApplicationInfo info = getApplicationInfo(pkg, Users.profile);
			callback.accept(info != null && (info.flags & FLAG_INSTALLED) != 0 ? info : null);
		} else if (! (activities = mLauncherApps.get().getActivityList(pkg, profile)).isEmpty()) {	// In case it has launcher activity and not frozen
			callback.accept(activities.get(0).getApplicationInfo());
		} else MethodShuttle.runInProfile(context, () -> {
			try {
				final ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, PM_FLAGS_GET_APP_INFO);
				return info != null && (info.flags & FLAG_INSTALLED) != 0 ? info : null;
			} catch (PackageManager.NameNotFoundException e) {
				return null;
			}
		}).thenAccept(callback).exceptionally(t -> {
			callback.accept(null);
			return null;
		});
	}

	@RequiresApi(N) ApplicationInfo getApplicationInfo(final String pkg, final UserHandle user) {
		try {
			return Objects.requireNonNull(Hacks.LauncherApps_getApplicationInfo).invoke(pkg, PM_FLAGS_GET_APP_INFO, user).on(mLauncherApps.get());
		} catch (final Exception e) {	// NameNotFoundException will be thrown since Android O instead of retuning null on Android N.
			if (e instanceof RuntimeException) throw (RuntimeException) e;
			return null;
		}
	}

	public void refreshPackage(final String pkg, final @Nullable UserHandle user, final boolean add) {
		if (! Objects.equals(user, Users.profile)) return;
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

	/** Freezing or disabling a critical app may cause malfunction to other apps or the whole system. */
	public boolean isCritical(final String pkg) {
		return (SDK_INT >= N && pkg.equals(CriticalAppsManager.getCurrentWebViewPackageName()))
				|| mCriticalSystemPackages.get().contains(pkg);
	}

	public Stream<IslandAppInfo> getCriticalSystemPackages() {
		return StreamSupport.stream(mIslandAppMap.get().values()).filter(app -> mCriticalSystemPackages.get().contains(app.packageName));
	}

	public boolean isHiddenSysAppCloned(final String pkg) {
		return mClonedHiddenSystemApps.get().isCloned(pkg);
	}

	public void setHiddenSysAppCloned(final String pkg) {
		if (Users.hasProfile()) mClonedHiddenSystemApps.get().setCloned(pkg);
	}

	private final LauncherApps.Callback mCallback = new LauncherApps.Callback() {

		@Override public void onPackageRemoved(final String pkg, final UserHandle user) {
			if (! user.equals(Users.profile)) return;
			final IslandAppInfo app = mIslandAppMap.get().get(pkg);
			if (app == null) {
				Log.e(TAG, "Removed package not found in Island: " + pkg);
				return;
			}
			if (app.isHidden()) return;		// The removal callback is triggered by freezing.
			queryApplicationInfoInProfile(pkg, info -> {
				if (info != null && (info.flags & FLAG_INSTALLED) != 0) {	// Frozen
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
			refreshPackage(pkg, user, false);		// TODO: Filter out component-level changes
		}

		@Override public void onPackagesAvailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesAvailable() is unsupported");
		}

		@Override public void onPackagesUnavailable(final String[] pkgs, final UserHandle user, final boolean replacing) {
			Log.e(TAG, "onPackagesUnavailable() is unsupported");
		}
	};

	private final Supplier<ConcurrentHashMap<String/* package */, IslandAppInfo>> mIslandAppMap = Suppliers.memoize(() -> {
		final ConcurrentHashMap<String, IslandAppInfo> apps = new ConcurrentHashMap<>();
		onStartLoadingIslandApps(apps);
		return apps;
	});

	private final Supplier<LauncherApps> mLauncherApps = Suppliers.memoize(() -> (LauncherApps) context().getSystemService(Context.LAUNCHER_APPS_SERVICE));
	@RequiresPermission(Permissions.INTERACT_ACROSS_USERS) private final Supplier<PackageManager> mProfilePackageManager = Suppliers.memoize(() ->
			ContextShuttle.getPackageManagerAsUser(context(), Users.profile));
	private final Supplier<ClonedHiddenSystemApps> mClonedHiddenSystemApps = Suppliers.memoize(() ->
			new ClonedHiddenSystemApps(context(), Users.profile, pkg -> refreshPackage(pkg, Users.profile, false)));
	private final Supplier<Set<String>> mCriticalSystemPackages = Suppliers.memoize(() ->
			SystemAppsManager.detectCriticalSystemPackages(context().getPackageManager()));

	private static final String TAG = "Island.AppListProv";
}
