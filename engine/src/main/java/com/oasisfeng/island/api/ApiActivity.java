package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.util.Log;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.oasisfeng.island.engine.BuildConfig;
import com.oasisfeng.island.engine.IslandManagerService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public static final String EXTRA_CALLER_ID = "caller";	// PendingIntent whose creator package is considered the caller of API
	public static final String EXTRA_ALWAYS = "always";		// Boolean (default: true)

	private static final Map<String/* pkg */, Integer/* signature hash */> sVerifiedCallers = new HashMap<>(1);
	static {
		sVerifiedCallers.put("com.oasisfeng.greenify", -373128424);
		if (BuildConfig.DEBUG) sVerifiedCallers.put("com.oasisfeng.greenify.debug", 0/* Any signature */);
	}

	/** The public API result code for unverified identity. */
	private static final int RESULT_UNVERIFIED_IDENTITY = Activity.RESULT_FIRST_USER;

	/** @return activity result or null for "DO NOT setResult()" */
	private @CheckResult Integer onStartCommand(final Intent intent) {
		if (intent.getAction() == null) return RESULT_CANCELED;
		String caller = getCallingPackage();
		if (caller == null) {
			final PendingIntent id = intent.getParcelableExtra(EXTRA_CALLER_ID);
			if (id == null) return RESULT_UNVERIFIED_IDENTITY;
			caller = id.getCreatorPackage();
			if (caller == null) return RESULT_UNVERIFIED_IDENTITY;
		}
		Log.v(TAG, "API caller: " + caller);
		if (! verifyCaller(caller)) return RESULT_UNVERIFIED_IDENTITY;

		switch (intent.getAction()) {
		case ACTION_GET_APP_LIST:
//			setResult(RESULT_OK, new Intent().setData(Uri.fromParts("packages", Joiner.on(',').join(getInstalledAppsExceptSelfInUser()), null)));
//			return null;
			return RESULT_CANCELED;
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
			try {
				final List<String> unqualified_pkgs = pkgs.filter(pkg -> (! always && isForeground(pkg))
						|| ! mIslandManager.get().freezeApp(pkg, "api")).toList();

				if (unqualified_pkgs.isEmpty()) return RESULT_OK;
				if (single) return RESULT_CANCELED;
				setResult(RESULT_OK, unqualified_pkgs.isEmpty() ? null :
						new Intent().setData(Uri.fromParts("packages", Joiner.on(',').join(unqualified_pkgs), null)));
				return null;
			} catch (final SecurityException e) {
				return RESULT_CANCELED;		// Island might be have been deactivated or not set up yet.
			}
		default: return RESULT_CANCELED;
		}
	}

	private boolean verifyCaller(final String pkg) {
		final Integer value = sVerifiedCallers.get(pkg);
		if (value == null) return false;
		final int signature_hash = value;
		if (signature_hash == 0) return true;
		try { @SuppressWarnings("deprecation") @SuppressLint("PackageManagerGetSignatures")
			final PackageInfo pkg_info = getPackageManager().getPackageInfo(pkg, PackageManager.GET_SIGNATURES | PackageManager.GET_UNINSTALLED_PACKAGES);
			for (final Signature signature : pkg_info.signatures)
				if (signature.hashCode() != signature_hash) return false;
			sVerifiedCallers.put(pkg, 0);		// No further signature check for this caller in the lifetime of this process.
			return true;
		} catch (final PackageManager.NameNotFoundException e) { return false; }		// Should hardly happen
	}

	private static boolean isForeground(final String pkg) {
		// TODO
		return false;
	}

//	private ArrayList<String> getInstalledAppsExceptSelfInUser() {
//		return IslandAppListProvider.getInstance(this).installedApps()
//				.filter(IslandAppListProvider.excludeSelf(this)).filter(IslandAppInfo::isInstalledInIsland)
//				.map(app -> app.packageName).collect(Collectors.toCollection(ArrayList::new));
//	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		onIntent(getIntent());
	}

	@Override protected void onNewIntent(final Intent intent) {
		setIntent(intent);
		onIntent(intent);
	}

	private void onIntent(final Intent intent) {
		Log.d(TAG, "API request: " + intent.toUri(0));
		final Integer result = onStartCommand(intent);
		if (result != null) setResult(result);
		Log.d(TAG, "API result:" + result);
		finish();
	}

	private final Supplier<IslandManagerService> mIslandManager = Suppliers.memoize(() -> new IslandManagerService(this));
	private static final String TAG = "API";
}
