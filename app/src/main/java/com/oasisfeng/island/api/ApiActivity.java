package com.oasisfeng.island.api;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;

import com.google.common.collect.ImmutableList;
import com.oasisfeng.island.data.AppList;
import com.oasisfeng.island.engine.IslandManager;

import java.util.ArrayList;

/**
 * API via activity to cross the user border
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	public static final String ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE";
	public static final String ACTION_GET_APP_LIST = "com.oasisfeng.island.action.GET_APP_LIST";
	public static final String EXTRA_API_TOKEN = "token";
	private static final String EXTRA_PACKAGE_LIST = "packages";

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
			final Uri uri = intent.getData();
			if (uri != null)
				new IslandManager(this).freezeApp(uri.getSchemeSpecificPart(), "api");
			setResult(RESULT_OK);
			break;
		}
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
}
