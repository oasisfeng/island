package com.oasisfeng.island.data;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.support.v4.content.ContextCompat;

import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.concurrent.TimeUnit;

import static android.content.Context.LAUNCHER_APPS_SERVICE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

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
		return enabled && (Users.isOwner(user) || ! isHidden() || ! isSystem() || ! shouldTreatHiddenSysAppAsDisabled());
	}

	private boolean shouldTreatHiddenSysAppAsDisabled() {
		return ! ((IslandAppListProvider) mProvider).isHiddenSysAppCloned(packageName);
	}

	public void stopTreatingHiddenSysAppAsDisabled() {
		((IslandAppListProvider) mProvider).setHiddenSysAppCloned(packageName);
	}

	public boolean isHidden() {
		final Boolean hidden = isHidden(this);
		if (hidden != null) return hidden;
		// The fallback implementation
		return ! ((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).isPackageEnabled(packageName, Process.myUserHandle());
	}

	/** @return hidden state, or null if failed to */
	private static Boolean isHidden(final ApplicationInfo info) {
		if (SDK_INT >= M) {
			final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(info);
			if (private_flags != null) return (private_flags & PRIVATE_FLAG_HIDDEN) != 0;
		} else return (info.flags & FLAG_HIDDEN) != 0;
		return null;
	}

	/** Is launchable (even if hidden) */
	@Override public boolean isLaunchable() { return mIsLaunchable.get(); }
	@SuppressWarnings("deprecation") private final Supplier<Boolean> mIsLaunchable = Suppliers.memoizeWithExpiration(
			() -> checkLaunchable(PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS), 1, TimeUnit.SECONDS);

	@Override protected boolean checkLaunchable(final int flags) {
		if (Users.isOwner(user)) return super.checkLaunchable(flags);
		if (ContextCompat.checkSelfPermission(context(), INTERACT_ACROSS_USERS) == PERMISSION_GRANTED && ! Hacks.PackageManager_resolveActivityAsUser.isAbsent()) {
			final Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName);
			final ResolveInfo resolved = Hacks.PackageManager_resolveActivityAsUser.invoke(intent, flags, Users.toId(user)).on(context().getPackageManager());
			return resolved != null;
		}
		return ! ((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).getActivityList(packageName, user).isEmpty();
	}

	@Override public IslandAppInfo getLastInfo() { return (IslandAppInfo) super.getLastInfo(); }

	IslandAppInfo(final IslandAppListProvider provider, final UserHandle user, final ApplicationInfo base, final IslandAppInfo last) {
		super(provider, base, last);
		this.user = user;
	}

	@Override public String toString() { return fillToString(MoreObjects.toStringHelper(IslandAppInfo.class)).toString(); }

	@Override public MoreObjects.ToStringHelper fillToString(final MoreObjects.ToStringHelper helper) {
		helper.add("user", Users.toId(user));
		super.fillToString(helper);
		if (isHidden()) helper.addValue(shouldTreatHiddenSysAppAsDisabled() ? "hidden (as disabled)" : "hidden");
		return helper;
	}

	public final UserHandle user;
}
