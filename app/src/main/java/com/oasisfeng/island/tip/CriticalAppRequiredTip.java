package com.oasisfeng.island.tip;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.CardView;

import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.controller.IslandAppClones;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.provisioning.CriticalAppsManager;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.ui.card.CardViewModel;

import java.util.List;
import java.util.Objects;

import java9.util.stream.Collectors;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Hint to solve web-view related issues / crashes, by cloning or unfreezing the current provider package of web-view.
 *
 * Created by Oasis on 2017/9/10.
 */
public class CriticalAppRequiredTip extends IgnorableTip {

	@WorkerThread @Override protected @Nullable CardViewModel buildCardIfNotIgnored(final Context context) {
		final String webview_pkg;
		if (SDK_INT >= N && Users.hasProfile() && (webview_pkg = CriticalAppsManager.getCurrentWebViewPackageName()) != null) {
			final IslandAppInfo app = IslandAppListProvider.getInstance(context).get(webview_pkg, Users.profile);
			if (! shouldIgnoreTip(context, webview_pkg)) {
				final CardViewModel card = buildCardIfActionRequired(context, webview_pkg, app);
				if (card != null) return card;
			}
		}
		if (mCriticalSystemPackages == null) mCriticalSystemPackages = IslandAppListProvider.getInstance(context).getCriticalSystemPackages()
				.filter(app -> app.isHidden() || ! app.enabled).collect(Collectors.toList());
		if (mCriticalSystemPackages.isEmpty()) return null;
		for (final IslandAppInfo app : mCriticalSystemPackages) {
			if (shouldIgnoreTip(context, app.packageName)) continue;
			return buildCardIfActionRequired(context, app.packageName, app);
		}
		return null;
	}

	@WorkerThread private CardViewModel buildCardIfActionRequired(final Context context, final String pkg, final @Nullable IslandAppInfo app) {
		if (app != null && app.enabled && ! app.isHidden()) return null;		// No action required for this app.
		final CardViewModel card = new CardViewModel(context, R.string.tip_critical_package_required, 0,
				getIgnoreActionLabel(), app == null ? R.string.action_clone : app.isHidden() ? R.string.action_unfreeze : R.string.action_app_settings) {

			@Override public void onButtonStartClick(final Context context, final CardView card) {
				dismiss(card);
				ignoreTip(context, pkg);
			}

			@Override public void onButtonEndClick(final Context context, final CardView card) {
				dismiss(card);
				if (app == null) {
					final ApplicationInfo app_info;
					try {
						app_info = context.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
					} catch (final PackageManager.NameNotFoundException e) { return; }	// Should never happen.
					MethodShuttle.runInProfile(context, () -> new IslandAppClones(context).cloneUserApp(pkg, app_info, true));
				} else if (app.isHidden()) {
					MethodShuttle.runInProfile(context, () -> IslandManager.ensureAppHiddenState(context, pkg, false));
				} else Objects.requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE))
						.startAppDetailsActivity(new ComponentName(pkg, ""), Users.profile, null, null);
			}
		};
		card.text = context.getString(R.string.tip_critical_package_message, app != null ? app.getLabel() : Apps.of(context).getAppName(pkg));
		return card;
	}

	CriticalAppRequiredTip() { super("tip-critical-app-required"); }

	private List<IslandAppInfo> mCriticalSystemPackages;
}
