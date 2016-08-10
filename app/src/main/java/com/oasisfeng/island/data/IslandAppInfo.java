package com.oasisfeng.island.data;

import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.util.Hacks;

import java.util.concurrent.TimeUnit;

import static android.content.Context.LAUNCHER_APPS_SERVICE;
import static com.oasisfeng.island.model.GlobalStatus.OWNER;

/**
 * Island-specific {@link AppInfo}
 *
 * Created by Oasis on 2016/8/10.
 */
public class IslandAppInfo extends AppInfo {

	private static final int PRIVATE_FLAG_HIDDEN = 1;
	private static final int FLAG_HIDDEN = 1<<27;

	public boolean isInstalledInUser() { return (flags & FLAG_INSTALLED) != 0; }
	public boolean checkInstalledInOwner() { return mIsInstalledInOwner.get(); }

	public boolean isHiddenOrNotInstalled() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(this);
			if (private_flags != null) return (private_flags & PRIVATE_FLAG_HIDDEN) != 0;
		} else return (flags & FLAG_HIDDEN) != 0;
		// The fallback implementation
		if (BuildConfig.DEBUG) Log.e(TAG, "Incompatible ROM: No field ApplicationInfo.privateFlags");
		return ! ((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).isPackageEnabled(packageName, Process.myUserHandle());
	}

	protected void onConfigurationChanged() {
		super.onConfigurationChanged();
	}

	IslandAppInfo(final IslandAppListProvider islandAppListProvider, final ApplicationInfo base, final IslandAppInfo last) {
		super(islandAppListProvider, base, last);
	}

	private final Supplier<Boolean> mIsInstalledInOwner = Suppliers.memoizeWithExpiration(
			() -> ((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).isPackageEnabled(packageName, OWNER),
			1, TimeUnit.SECONDS);	// Use short expiration to avoid repeated check in binding. TODO: Better solution?

	private static final String TAG = "Island.AppInfo";
}
