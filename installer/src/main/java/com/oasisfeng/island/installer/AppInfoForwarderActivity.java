package com.oasisfeng.island.installer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

import com.oasisfeng.android.content.IntentCompat;
import com.oasisfeng.android.os.UserHandles;
import com.oasisfeng.island.shuttle.ActivityShuttle;
import com.oasisfeng.island.util.CallerAwareActivity;
import com.oasisfeng.island.util.Users;

import java.util.List;

import androidx.annotation.Nullable;

import static android.content.Intent.EXTRA_INITIAL_INTENTS;
import static android.content.Intent.EXTRA_USER;
import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static java9.util.stream.StreamSupport.stream;

/**
 * Created by Oasis on 2018-11-16.
 */
public class AppInfoForwarderActivity extends CallerAwareActivity {

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent().setComponent(null).setPackage(null);
		final UserHandle user = intent.getParcelableExtra(EXTRA_USER);
		startActivity(buildChooser(intent.getStringExtra(IntentCompat.EXTRA_PACKAGE_NAME), user != null ? user : Process.myUserHandle(), intent));
		finish();
	}

	private Intent buildChooser(final String pkg, final UserHandle user, final Intent target) {
		final PackageManager pm = getPackageManager();
		final String caller = getCallingPackage();
		Intent app_detail = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
		final List<ResolveInfo> app_detail_resolves = pm.queryIntentActivities(app_detail, 0);    // Must before forceForwardingToIsland()
		final ResolveInfo app_detail_resolve = app_detail_resolves == null || app_detail_resolves.isEmpty() ? null : app_detail_resolves.get(0);
		final boolean caller_is_settings = app_detail_resolve != null && app_detail_resolve.activityInfo.packageName.equals(caller);
		if (! caller_is_settings && ! UserHandles.MY_USER_HANDLE.equals(user)) {
			if (user.equals(Users.profile) && app_detail_resolve != null) {
				app_detail.setComponent(ActivityShuttle.selectForwarder(app_detail_resolves));	// ACTION_APPLICATION_DETAILS_SETTINGS was added to forwarding by IslandProvisioning
				// Use mainland resolve to replace the misleading forwarding-resolved "Switch to work profile".
				final ActivityInfo activity = app_detail_resolve.activityInfo;
				app_detail = new LabeledIntent(app_detail, activity.packageName,
						activity.labelRes != 0 ? activity.labelRes : activity.applicationInfo.labelRes, activity.getIconResource());
			} else app_detail = null;    // TODO: Not the default managed profile, use LauncherApps.startAppDetailsActivity().
		}

		if (SDK_INT < O && ! caller_is_settings && app_detail != null && ! hasNonForwardingResolves(pm, target))
			return ActivityShuttle.forceNeverForwarding(pm, app_detail);    // Simulate EXTRA_AUTO_LAUNCH_SINGLE_CHOICE on Android pre-O.
		final Intent chooser = Intent.createChooser(target, getString(R.string.app_info_forwarder_title)).addFlags(FLAG_ACTIVITY_FORWARD_RESULT);
		if (SDK_INT >= O) chooser.putExtra(IntentCompat.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, ! caller_is_settings);
		if (app_detail != null && ! caller_is_settings) chooser.putExtra(EXTRA_INITIAL_INTENTS, new Parcelable[] { app_detail });
		return chooser;
	}

	private static boolean hasNonForwardingResolves(final PackageManager pm, final Intent intent) {
		final List<ResolveInfo> candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY/* Excluding this activity */);
		return candidates != null && stream(candidates).anyMatch(candidate -> ! "android".equals(candidate.activityInfo.packageName));
	}
}
