package com.oasisfeng.island.data;

import static android.content.Context.LAUNCHER_APPS_SERVICE;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Island-specific {@link AppInfo}
 *
 * Created by Oasis on 2016/8/10.
 */
public class IslandAppInfo extends AppInfo {

	void setHidden(final boolean state) {
		final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(this);
		if (private_flags != null)
			Hacks.ApplicationInfo_privateFlags.set(this, state ? private_flags | PRIVATE_FLAG_HIDDEN : private_flags & ~ PRIVATE_FLAG_HIDDEN);
	}

	/** Some system apps are hidden by post-provisioning, they should be treated as "disabled". */
	public boolean shouldShowAsEnabled() {
		return enabled && ! isHiddenSysIslandAppTreatedAsDisabled();
	}

	public boolean isHiddenSysIslandAppTreatedAsDisabled() {
		return isSystem() && isHidden() && shouldTreatHiddenSysAppAsDisabled();
	}

	private boolean shouldTreatHiddenSysAppAsDisabled() {
		return ! ClonedHiddenSystemApps.isCloned(this);
	}

	/** @return whether this package is critical to the system, thus should not be frozen or disabled. */
	public boolean isCritical() {
		return ((IslandAppListProvider) mProvider).isCritical(packageName);
	}

	/** Is launchable (even if hidden) */
	@Override public boolean isLaunchable() { return mIsLaunchable.get(); }
	private final Supplier<Boolean> mIsLaunchable = Suppliers.memoizeWithExpiration(	// Use GET_DISABLED_COMPONENTS in case mainland sibling is disabled.
			() -> checkLaunchable(Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED | MATCH_DISABLED_COMPONENTS), 1, SECONDS);

	@Override protected boolean checkLaunchable(final int flags_for_resolve) {
		if (! Users.isParentProfile(user) && ! isHidden()) {		// Accurate detection for non-frozen app in Island
			if (sLaunchableNonFrozenIslandAppsCache != null) {
				final Set<String> launchable = sLaunchableNonFrozenIslandAppsCache.get(user);
				if (launchable != null) return launchable.contains(packageName);
			}
			try { return ! requireNonNull((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).getActivityList(packageName, user).isEmpty(); }
			catch (final SecurityException e) { return false; } // "SecurityException: Cannot retrieve activities for unrelated profile NNN" appeared on OPPO A3s and Vivo 1718 (both Android 8.1).
		}
		if (sPotentiallyLaunchableAppsCache != null) return sPotentiallyLaunchableAppsCache.contains(packageName);
		return super.checkLaunchable(flags_for_resolve);	// Inaccurate detection for frozen app (false-positive if launcher activity is actually disabled)
	}

	public static void cacheLaunchableApps(final Context context) {
		if (Users.hasProfile()) {
			sLaunchableNonFrozenIslandAppsCache = new ArrayMap<>();
			final LauncherApps la = requireNonNull(context.getSystemService(LauncherApps.class));
			for (final UserHandle profile : Users.getProfilesManagedByIsland()) {
				final Set<String> apps = la.getActivityList(null, profile).stream().map(lai ->
						lai.getComponentName().getPackageName()).collect(toSet());
				sLaunchableNonFrozenIslandAppsCache.put(profile, apps);
			}
		}
		@SuppressLint("WrongConstant") final List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(
				new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED | MATCH_DISABLED_COMPONENTS);
		sPotentiallyLaunchableAppsCache = activities.stream().map(resolve -> resolve.activityInfo.packageName).collect(toSet());
	}

	public static void invalidateLaunchableAppsCache() { sLaunchableNonFrozenIslandAppsCache = null; sPotentiallyLaunchableAppsCache = null; }

	private static Map<UserHandle, Set<String>> sLaunchableNonFrozenIslandAppsCache;
	private static Set<String> sPotentiallyLaunchableAppsCache;

	@Override public IslandAppInfo getLastInfo() { return (IslandAppInfo) super.getLastInfo(); }

	public IslandAppInfo cloneWithLabel(final CharSequence label) {
		return new IslandAppInfo((IslandAppListProvider) mProvider, user, this, getLastInfo(), label);
	}

	IslandAppInfo(final IslandAppListProvider provider, final UserHandle user, final ApplicationInfo base, final @Nullable IslandAppInfo last) {
		this(provider, user, base, last, null);
	}

	IslandAppInfo(final IslandAppListProvider provider, final UserHandle user, final ApplicationInfo base,
	                      final @Nullable IslandAppInfo last, final @Nullable CharSequence label) {
		super(provider, base, last, label);
		this.user = user;
	}

	@Override public StringBuilder buildToString(final Class<?> clazz) {
		final StringBuilder builder = super.buildToString(clazz).append(", user ").append(Users.toId(user));
		if (isHidden()) builder.append(! Users.isParentProfile(user) && shouldTreatHiddenSysAppAsDisabled() ? ", hidden (as disabled)" : ", hidden");
		return builder;
	}

	public final UserHandle user;
}
