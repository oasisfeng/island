package com.oasisfeng.island.shuttle;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * Shuttle for cross-profile activity behaviors.
 *
 * Created by Oasis on 2019-3-4.
 */
public class ActivityShuttle {

	public static Intent forceNeverForwarding(final PackageManager pm, final Intent intent) {
		final List<ResolveInfo> candidates = pm.queryIntentActivities(intent, 0);
		if (candidates != null) for (final ResolveInfo candidate : candidates) {
			if ("android".equals(candidate.activityInfo.packageName)) continue;
			return intent.setComponent(new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name));
		}
		return intent;
	}

	public static ComponentName selectForwarder(final List<ResolveInfo> candidates) {
		if (candidates != null) for (final ResolveInfo candidate : candidates) {
			if (candidate.activityInfo == null || ! "android".equals(candidate.activityInfo.packageName)) continue;
			return new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name);
		}
		return null;
	}
}
