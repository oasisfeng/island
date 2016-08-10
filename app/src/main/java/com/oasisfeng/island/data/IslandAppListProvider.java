package com.oasisfeng.island.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.google.common.collect.Ordering;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.engine.SystemAppsManager;

import java.util.Collection;
import java.util.Collections;

import java8.util.function.Predicate;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;

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

	// Cloned apps first to optimize the label and icon loading experience.
	public static Ordering<ApplicationInfo> CLONED_FIRST = Ordering.explicit(true, false).onResultOf(info -> (info.flags & FLAG_INSTALLED) != 0);

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
		return new IslandAppInfo(this, base, last);
	}
}
