package com.oasisfeng.island.api;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.oasisfeng.island.data.AppList;
import com.oasisfeng.island.engine.IslandManager;

import java.util.ArrayList;
import java.util.List;

/**
 * API via activity to cross the user border
 *
 * <ol>
 * <li>1. Freeze specified app</li>
 * {@link #ACTION_FREEZE} with app package name in data of "package" scheme.
 * <p>Result: {@link Activity#RESULT_OK} for success or {@link Activity#RESULT_CANCELED} for failure
 *
 * <li>2. Freeze batched apps if not in foreground</li>
 * {@link #ACTION_FREEZE} with app package names in {@link #EXTRA_PACKAGE_LIST}
 * <p>Result: {@link Activity#RESULT_OK} for success / partial success, with unqualified / failed
 * apps in nullable {@link #EXTRA_PACKAGE_LIST}, or {@link Activity#RESULT_CANCELED} for other failures.
 * </ol>
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	public static final String ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE";
	public static final String ACTION_GET_APP_LIST = "com.oasisfeng.island.action.GET_APP_LIST";
	public static final String EXTRA_API_TOKEN = "token";
	private static final String EXTRA_PACKAGE_LIST = "packages";	// ArrayList<String>

	/** The public API result code for invalid API token, a new token must be requested. */
	private static final int RESULT_INVALID_TOKEN = Activity.RESULT_FIRST_USER;

	private void onStartCommand(final Intent intent) {
		if (intent.getAction() == null) return;
		if (! mApiTokens.verifyToken(intent.getStringExtra(EXTRA_API_TOKEN))) {
			setResult(RESULT_INVALID_TOKEN);
			return;
		}
		switch (intent.getAction()) {
		case ACTION_GET_APP_LIST:
			setResult(RESULT_OK, new Intent().putStringArrayListExtra(EXTRA_PACKAGE_LIST, getInstalledApps()));
			break;
		case ACTION_FREEZE:
			final Uri uri = intent.getData(); final ArrayList<String> pkgs;
			if (uri != null) {
				final String pkg = uri.getSchemeSpecificPart();
				final boolean result = pkg != null && mIslandManager.get().freezeApp(pkg, "api");
				setResult(result ? RESULT_OK : RESULT_CANCELED);
			} else if ((pkgs = intent.getStringArrayListExtra(EXTRA_PACKAGE_LIST)) != null) {
				final List<String> unqualified_pkgs = FluentIterable.from(pkgs).filter(pkg ->
						isForeground(pkg) || ! mIslandManager.get().freezeApp(pkg, "api")).toList();
				setResult(RESULT_OK, unqualified_pkgs.isEmpty() ? null :
						new Intent().putStringArrayListExtra(EXTRA_PACKAGE_LIST, Lists.newArrayList(unqualified_pkgs)));
			} else setResult(RESULT_CANCELED);
			break;
		}
	}

	private boolean isForeground(final String pkg) {
		// TODO
		return false;
	}

	private ArrayList<String> getInstalledApps() {
		final ImmutableList<ApplicationInfo> apps = AppList.all(this).excludeSelf().build().toSortedList(AppList.CLONED_FIRST);
		final ArrayList<String> app_list = new ArrayList<>(apps.size());
		for (final ApplicationInfo app : apps) app_list.add(app.packageName);
		return app_list;
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApiTokens = new ApiTokenManager(this);
		onStartCommand(getIntent());
		finish();
	}

	@Override protected void onNewIntent(final Intent intent) {
		setIntent(intent);
		onStartCommand(intent);
		finish();
	}

	private ApiTokenManager mApiTokens;
	private final Supplier<IslandManager> mIslandManager = Suppliers.memoize(() -> new IslandManager(this));
}
