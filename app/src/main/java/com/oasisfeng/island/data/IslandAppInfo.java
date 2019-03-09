package com.oasisfeng.island.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

import static android.content.Context.LAUNCHER_APPS_SERVICE;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.pm.PackageManager.GET_DISABLED_COMPONENTS;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Process.myUserHandle;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Island-specific {@link AppInfo}
 *
 * Created by Oasis on 2016/8/10.
 */
public class IslandAppInfo extends AppInfo {

	private static final int PRIVATE_FLAG_HIDDEN = 1;
	private static final int FLAG_HIDDEN = 1<<27;

	void setHidden(final boolean state) {
		if (SDK_INT >= M) {
			final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(this);
			if (private_flags != null)
				Hacks.ApplicationInfo_privateFlags.set(this, state ? private_flags | PRIVATE_FLAG_HIDDEN : private_flags & ~ PRIVATE_FLAG_HIDDEN);
		} else if (state) flags |= FLAG_HIDDEN;
		else flags &= ~ FLAG_HIDDEN;
	}

	/** Some system apps are hidden by post-provisioning, they should be treated as "disabled". */
	public boolean shouldShowAsEnabled() {
		return enabled && ! isHiddenSysIslandAppTreatedAsDisabled();
	}

	public boolean isHiddenSysIslandAppTreatedAsDisabled() {	// Users.isProfile() is not checked
		return Users.isProfile(user) && isSystem() && isHidden() && shouldTreatHiddenSysAppAsDisabled();
	}

	private boolean shouldTreatHiddenSysAppAsDisabled() {
		return ! ((IslandAppListProvider) mProvider).isHiddenSysAppCloned(packageName);
	}

	public void stopTreatingHiddenSysAppAsDisabled() {
		if (isSystem()) ((IslandAppListProvider) mProvider).setHiddenSysAppCloned(packageName);
	}

	public boolean isHidden() {
		final Boolean hidden = isHidden(this);
		if (hidden != null) return hidden;
		// The fallback implementation
		return ! Objects.requireNonNull((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).isPackageEnabled(packageName, myUserHandle());
	}

	/** @return hidden state, or null if failed to */
	private static Boolean isHidden(final ApplicationInfo info) {
		if (SDK_INT >= M) {
			final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(info);
			if (private_flags != null) return (private_flags & PRIVATE_FLAG_HIDDEN) != 0;
		} else return (info.flags & FLAG_HIDDEN) != 0;
		return null;
	}

	/** @return whether this package is critical to the system, thus should not be frozen or disabled. */
	public boolean isCritical() {
		return ((IslandAppListProvider) mProvider).isCritical(packageName);
	}

	/** Is launchable (even if hidden) */
	@Override public boolean isLaunchable() { return mIsLaunchable.get(); }
	private final Supplier<Boolean> mIsLaunchable = Suppliers.memoizeWithExpiration(	// Use GET_DISABLED_COMPONENTS in case mainland sibling is disabled.
			() -> checkLaunchable(Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED | GET_DISABLED_COMPONENTS), 1, SECONDS);

	@Override protected boolean checkLaunchable(final int flags_for_resolve) {
		if (sLaunchableAppsCache != null) return sLaunchableAppsCache.contains(packageName);
		return super.checkLaunchable(flags_for_resolve);
	}

	public static void cacheLaunchableApps(final Context context) {
		@SuppressLint("WrongConstant") final List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(
				new Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED | GET_DISABLED_COMPONENTS);
		sLaunchableAppsCache = StreamSupport.stream(activities).map(resolve -> resolve.activityInfo.packageName).collect(Collectors.toSet());
	}

	public static void invalidateLaunchableAppsCache() { sLaunchableAppsCache = null; }

	private static Set<String> sLaunchableAppsCache;

	@Override public IslandAppInfo getLastInfo() { return (IslandAppInfo) super.getLastInfo(); }

	IslandAppInfo(final IslandAppListProvider provider, final UserHandle user, final ApplicationInfo base, final IslandAppInfo last) {
		super(provider, base, last);
		this.user = user;
	}

	@Override public StringBuilder buildToString(final Class<?> clazz) {
		final StringBuilder builder = super.buildToString(clazz).append(", user ").append(Users.toId(user));
		if (isHidden()) builder.append(! Users.isOwner(user) && shouldTreatHiddenSysAppAsDisabled() ? ", hidden (as disabled)" : ", hidden");
		return builder;
	}

	public final UserHandle user;
}
