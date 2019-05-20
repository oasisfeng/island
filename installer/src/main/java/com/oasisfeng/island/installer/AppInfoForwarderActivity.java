package com.oasisfeng.island.installer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;
import android.provider.Settings;

import com.oasisfeng.android.content.IntentCompat;
import com.oasisfeng.android.os.UserHandles;
import com.oasisfeng.island.shuttle.ActivityShuttle;
import com.oasisfeng.island.util.CallerAwareActivity;
import com.oasisfeng.island.util.Users;

import androidx.annotation.Nullable;

import static android.content.Intent.EXTRA_INITIAL_INTENTS;
import static android.content.Intent.EXTRA_USER;
import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.os.Process.myUserHandle;

/**
 * Created by Oasis on 2018-11-16.
 */
public class AppInfoForwarderActivity extends CallerAwareActivity {

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent().setComponent(null).setPackage(null);
		startActivity(buildAppSettingsChooser(intent.getStringExtra(IntentCompat.EXTRA_PACKAGE_NAME), myUserHandle(), intent));
		finish();
	}

	private Intent buildAppSettingsChooser(final String pkg, final UserHandle user, final Intent target) {
		final String caller = getCallingPackage();
		Intent app_detail = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
		final ResolveInfo app_detail_resolve = getPackageManager().resolveActivity(app_detail, 0);	// Must resolve before forceForwardingToIsland()
		final boolean caller_is_settings = app_detail_resolve != null && app_detail_resolve.activityInfo.packageName.equals(caller);
		if (! caller_is_settings && ! UserHandles.MY_USER_HANDLE.equals(user)) {
			if (user.equals(Users.profile) && app_detail_resolve != null) {
				ActivityShuttle.forceForwardingToIsland(getPackageManager(), app_detail);	// ACTION_APPLICATION_DETAILS_SETTINGS is whitelisted by provisioning
				// Use mainland resolve to replace the misleading forwarding-resolved "Switch to work profile".
				final ActivityInfo activity = app_detail_resolve.activityInfo;
				app_detail = new LabeledIntent(app_detail, activity.packageName,
						activity.labelRes != 0 ? activity.labelRes : activity.applicationInfo.labelRes, activity.getIconResource());
			} else app_detail = null;    // TODO: Not the default managed profile, use LauncherApps.startAppDetailsActivity().
		}

		final Intent chooser = Intent.createChooser(target.putExtra(EXTRA_USER, myUserHandle()), getString(R.string.app_info_forwarder_title));
		if (app_detail != null) chooser.putExtra(EXTRA_INITIAL_INTENTS, new Parcelable[] { app_detail });
		return chooser.putExtra(IntentCompat.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, ! caller_is_settings).addFlags(FLAG_ACTIVITY_FORWARD_RESULT);
	}
}
