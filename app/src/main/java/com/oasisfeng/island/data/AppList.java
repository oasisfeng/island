package com.oasisfeng.island.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import com.oasisfeng.island.engine.SystemAppsManager;

import java.util.Collection;
import java.util.Collections;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.PackageManager.GET_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static com.google.common.collect.FluentIterable.from;

/**
 * App list
 * Created by Oasis on 2016/6/2.
 */

public class AppList {

	/** System packages shown to user always even if no launcher activities */
	public static final Collection<String> ALWAYS_VISIBLE_SYS_PKGS = Collections.singletonList("com.google.android.gms");

	public static final Predicate<ApplicationInfo> INSTALLED = app -> (app.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
	public static final Predicate<ApplicationInfo> NON_SYSTEM = app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
	public static final Predicate<ApplicationInfo> NON_CRITICAL_SYSTEM = app -> ! SystemAppsManager.isCritical(app.packageName);
	public final Predicate<ApplicationInfo> LAUNCHABLE = app -> isLaunchable(AppList.this.mContext, app.packageName);

	// Cloned apps first to optimize the label and icon loading experience.
	public static Ordering<ApplicationInfo> CLONED_FIRST = Ordering.explicit(true, false).onResultOf(info -> (info.flags & FLAG_INSTALLED) != 0);

	public AppList exclude(final String pkg) { return new AppList(mContext, mBuilder.filter(app -> ! pkg.equals(app.packageName))); }
	public AppList excludeSelf() { return exclude(mContext.getPackageName()); }

	public static AppList all(final Context context) { return fromInstalledApps(context, GET_UNINSTALLED_PACKAGES); }
	public static AppList available(final Context context) { return fromInstalledApps(context, 0); }

	private static AppList fromInstalledApps(final Context context, final int flags) {
		return new AppList(context, context.getPackageManager().getInstalledApplications(flags));
	}

	public FluentIterable<ApplicationInfo> build() {
		return mBuilder;
	}

	private static boolean isLaunchable(final Context context, final String pkg) {
		final PackageManager pm = context.getPackageManager();
		final Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
		@SuppressWarnings("WrongConstant") final ResolveInfo resolved = pm.resolveActivity(intent, GET_DISABLED_COMPONENTS | GET_UNINSTALLED_PACKAGES);
		return resolved != null;
	}

	private AppList(final Context context, final Iterable<ApplicationInfo> apps) {
		mContext = context;
		mBuilder = from(apps);
	}

	private final Context mContext;
	private final FluentIterable<ApplicationInfo> mBuilder;
}
