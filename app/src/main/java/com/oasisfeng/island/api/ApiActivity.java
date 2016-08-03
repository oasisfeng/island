package com.oasisfeng.island.api;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CheckResult;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.oasisfeng.island.data.AppList;
import com.oasisfeng.island.engine.IslandManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API via activity to cross the user border
 *
 * <ol>
 * <li>1. Freeze specified app</li>
 * {@link #ACTION_FREEZE} with app package name in data of "package" scheme.<br/>
 * {@link #EXTRA_ALWAYS} (default: true) freeze the given apps regardless of their running state.
 * <p>Result: {@link Activity#RESULT_OK} for success or {@link Activity#RESULT_CANCELED} for failure
 *
 * <li>2. Freeze batched apps</li>
 * {@link #ACTION_FREEZE} with app package names in data of "packages" scheme, comma-separated.<br/>
 * {@link #EXTRA_ALWAYS} (default: true) freeze the given apps regardless of their running state.
 * <p>Result: {@link Activity#RESULT_OK} for success / partial success, with unqualified / failed
 * apps in nullable data of "packages" scheme, or {@link Activity#RESULT_CANCELED} for other failures.
 * </ol>
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	public static final String ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE";
	public static final String ACTION_GET_APP_LIST = "com.oasisfeng.island.action.GET_APP_LIST";
	public static final String EXTRA_API_TOKEN = "token";	// String
	public static final String EXTRA_ALWAYS = "always";		// Boolean (default: true)

	/** The public API result code for invalid API token, a new token must be requested. */
	private static final int RESULT_INVALID_TOKEN = Activity.RESULT_FIRST_USER;

	/** @return activity result or null for "DO NOT setResult()" */
	private @CheckResult Integer onStartCommand(final Intent intent) {
		if (intent.getAction() == null) return RESULT_CANCELED;
		if (! mApiTokens.verifyToken(intent.getStringExtra(EXTRA_API_TOKEN))) return RESULT_INVALID_TOKEN;

		switch (intent.getAction()) {
		case ACTION_GET_APP_LIST:
			setResult(RESULT_OK, new Intent().setData(Uri.fromParts("packages", Joiner.on(',').join(getInstalledApps()), null)));
			return null;
		case ACTION_FREEZE:
			final Uri uri = intent.getData(); final String ssp;
			if (uri == null || (ssp = uri.getSchemeSpecificPart()) == null) return RESULT_CANCELED;
			final String scheme = uri.getScheme();
			final FluentIterable<String> pkgs;
			final boolean single;
			if (single = "package".equals(scheme)) pkgs = FluentIterable.from(Collections.singleton(ssp));
			else if ("packages".equals(scheme)) pkgs = FluentIterable.from(Splitter.on(',').split(ssp));
			else return RESULT_CANCELED;	// Should never happen

			final boolean always = intent.getBooleanExtra(EXTRA_ALWAYS, true);
			final List<String> unqualified_pkgs = pkgs.filter(pkg -> (! always && isForeground(pkg))
					|| ! mIslandManager.get().freezeApp(pkg, "api")).toList();

			if (unqualified_pkgs.isEmpty()) return RESULT_OK;
			if (single) return RESULT_CANCELED;
			setResult(RESULT_OK, unqualified_pkgs.isEmpty() ? null :
					new Intent().setData(Uri.fromParts("packages", Joiner.on(',').join(unqualified_pkgs), null)));
			return null;
		default: return RESULT_CANCELED;
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
		onIntent(getIntent());
	}

	@Override protected void onNewIntent(final Intent intent) {
		setIntent(intent);
		onIntent(intent);
	}

	private void onIntent(final Intent intent) {
		final Integer result = onStartCommand(intent);
		if (result != null) setResult(result);
		finish();
	}

	private ApiTokenManager mApiTokens;
	private final Supplier<IslandManager> mIslandManager = Suppliers.memoize(() -> new IslandManager(this));
}
