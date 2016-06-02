package com.oasisfeng.island.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;

/**
 * App list
 * Created by Oasis on 2016/6/2.
 */

public class AppList {

	/** System packages shown to user always even if no launcher activities */
	public static final Collection<String> ALWAYS_VISIBLE_SYS_PKGS = Collections.singletonList("com.google.android.gms");

	// Cloned apps first to optimize the label and icon loading experience.
	public static Ordering<ApplicationInfo> CLONED_FIRST = Ordering.explicit(true, false).onResultOf(info -> (info.flags & FLAG_INSTALLED) != 0);

	public static FluentIterable<ApplicationInfo> populate(final Context context) {
		final String this_pkg = context.getPackageName();
		//noinspection WrongConstant
		final List<ApplicationInfo> installed_apps = context.getPackageManager().getInstalledApplications(GET_UNINSTALLED_PACKAGES);
		return FluentIterable.from(installed_apps).filter(app -> ! this_pkg.equals(app.packageName));	// Exclude Island
	}
}
