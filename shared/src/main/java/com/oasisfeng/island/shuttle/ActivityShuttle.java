package com.oasisfeng.island.shuttle;

import static android.content.Context.USER_SERVICE;
import static java.util.Objects.requireNonNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.oasisfeng.island.util.Users;

import java.util.List;

/**
 * Shuttle for cross-profile activity behaviors.
 *
 * Created by Oasis on 2019-3-4.
 */
public class ActivityShuttle {

	public static boolean canForwardTo(final Context context, final UserHandle user) {
		final List<UserHandle> profiles = requireNonNull(context.getSystemService(UserManager.class)).getUserProfiles();
		for (final UserHandle profile : profiles) {
			if (! Users.isParentProfile(profile)) return profile.equals(user);
		}
		return false;
	}

	public static Intent forceNeverForwarding(final PackageManager pm, final Intent intent) {
		final List<ResolveInfo> candidates = pm.queryIntentActivities(intent, 0);
		if (candidates != null) for (final ResolveInfo candidate : candidates) {
			if ("android".equals(candidate.activityInfo.packageName)) continue;
			return intent.setComponent(new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name));
		}
		return intent;
	}

	public static ComponentName getForwarder(final Context context) {
		final Intent intent = new Intent(Intent.ACTION_SEND).setType("*/*");    // Forwarding added by IslandProvisioning
		return selectForwarder(context.getPackageManager().queryIntentActivities(intent, 0));
	}

	private static ComponentName selectForwarder(final List<ResolveInfo> candidates) {
		if (candidates != null) for (final ResolveInfo candidate : candidates) {
			if (candidate.activityInfo == null || ! "android".equals(candidate.activityInfo.packageName)) continue;
			return new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name);
		}
		return null;
	}
}
