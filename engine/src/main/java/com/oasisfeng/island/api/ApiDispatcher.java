package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Process;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.oasisfeng.island.engine.BuildConfig;
import com.oasisfeng.island.engine.IslandManagerService;
import com.oasisfeng.island.util.Hacks;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java8.util.function.Predicate;

import static android.content.pm.PackageManager.GET_SIGNATURES;

/**
 * Dispatch the API calls.
 *
 * Created by Oasis on 2017/9/20.
 */
class ApiDispatcher {

	/** @return null if verified, or error message for debugging purpose (NOT part of the API protocol). */
	static String verifyCaller(final Context context, final Intent intent, final @Nullable String caller) {
		final String pkg;
		if (caller == null) {
			final PendingIntent id = intent.getParcelableExtra(Api.latest.EXTRA_CALLER_ID);
			if (id == null) return "Missing required extra (PendingIntent): " + Api.latest.EXTRA_CALLER_ID;
			final int uid = id.getCreatorUid();
			if (uid == Process.myUid()) return null;	// From same UID.
			pkg = id.getCreatorPackage();
			if (pkg == null) return "No creator information in PendingIntent: " + id;
		} else pkg = caller;
		Log.v(TAG, "API caller: " + pkg);

		final Integer value = sVerifiedCallers.get(pkg);
		if (value == null) return "Unauthorized client: " + pkg;
		final int signature_hash = value;
		if (signature_hash == 0) return null;
		try { @SuppressWarnings("deprecation") @SuppressLint({"PackageManagerGetSignatures", "WrongConstant"})
			final PackageInfo pkg_info = context.getPackageManager().getPackageInfo(pkg, GET_SIGNATURES | Hacks.MATCH_ANY_USER_AND_UNINSTALLED);
			for (final Signature signature : pkg_info.signatures)
				if (signature.hashCode() != signature_hash) return "Package signature mismatch";
			sVerifiedCallers.put(pkg, 0);		// No further signature check for this caller in the lifetime of this process.
			return null;
		} catch (final PackageManager.NameNotFoundException e) { return "Client package not found: " + pkg; }		// Should hardly happen
	}

	/** @return null for success, or error message for debugging purpose (NOT part of the API protocol). */
	static String dispatch(final Context context, final Intent intent) {
		switch (intent.getAction()) {
		case Api.latest.ACTION_FREEZE:
			final IslandManagerService island = new IslandManagerService(context);
			return processPackageUri(intent.getData(), pkg -> island.freezeApp(pkg, "api"));
		case Api.latest.ACTION_UNFREEZE:
			return processPackageUri(intent.getData(), new IslandManagerService(context)::unfreezeApp);
		default: return "Unsupported action: " + intent.getAction();
		}
	}

	private static String processPackageUri(final Uri uri, final Predicate<String> dealer) {
		final String ssp;
		if (uri == null || (ssp = uri.getSchemeSpecificPart()) == null) return "Invalid intent data: " + uri;
		final String scheme = uri.getScheme();
		final FluentIterable<String> pkgs;
		final boolean single;
		if (single = "package".equals(scheme)) pkgs = FluentIterable.from(Collections.singleton(ssp));
		else if ("packages".equals(scheme)) pkgs = FluentIterable.from(Splitter.on(',').split(ssp));
		else return "Unsupported intent data scheme: " + uri;	// Should never happen

		try {
			final List<String> failed_pkgs = pkgs.filter(t -> ! dealer.test(t)).toList();
			if (failed_pkgs.isEmpty()) return null;
			if (single) return "Failed to freeze: " + ssp;
			return "Failed to freeze: " + failed_pkgs;
		} catch (final SecurityException e) {
			return "Internal exception: " + e;		// Island might be have been deactivated or not set up yet.
		}
	}

	private static final Map<String/* pkg */, Integer/* signature hash */> sVerifiedCallers = new HashMap<>(1);
	static {
		sVerifiedCallers.put("com.oasisfeng.greenify", -373128424);
	}

	private static final String TAG = "API";
}
