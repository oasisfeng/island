package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;

import com.google.common.collect.ImmutableList;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.data.AppList;
import com.oasisfeng.island.engine.IslandManager;

import java.util.ArrayList;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;

/**
 * API via activity to cross the user border
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	public static final String ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE";
	public static final String ACTION_GET_APP_LIST = "com.oasisfeng.island.action.GET_APP_LIST";
	private static final String EXTRA_PACKAGE_LIST = "packages";

	private static final String ALLOWED_CALLER_PACKAGE = "com.oasisfeng.greenify";
	private static final int KGreenifySignatureHash = -373128424;   // Signature hash code of Greenify

	private void onStartCommand(final Intent intent) {
		if (intent.getAction() == null) return;
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
		final ImmutableList<ApplicationInfo> apps = AppList.available(this).excludeSelf().build().toSortedList(AppList.CLONED_FIRST);
		final ArrayList<String> app_list = new ArrayList<>(apps.size());
		for (final ApplicationInfo app : apps) app_list.add(app.packageName);
		return app_list;
	}

	private boolean validateCallerIdentity() {
		final String caller = getCallingPackage();
		if (! ALLOWED_CALLER_PACKAGE.equals(caller)) return false;
		try { @SuppressWarnings("WrongConstant") @SuppressLint("PackageManagerGetSignatures")
			final PackageInfo info = getPackageManager().getPackageInfo(caller, GET_SIGNATURES | GET_UNINSTALLED_PACKAGES);
			for (final Signature signature : info.signatures)
				if (signature.hashCode() != KGreenifySignatureHash) return false;
			return true;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (BuildConfig.DEBUG || validateCallerIdentity())
			onStartCommand(getIntent());
		finish();
	}
}
