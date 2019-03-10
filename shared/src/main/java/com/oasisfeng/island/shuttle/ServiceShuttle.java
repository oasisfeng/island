package com.oasisfeng.island.shuttle;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.analytics.Analytics;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import java9.util.stream.StreamSupport;

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_NO_HISTORY;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;

/**
 * Mechanism create binding from owner user to service in profile, via a proxy activity.

 * Created by Oasis on 2017/2/20.
 */
public class ServiceShuttle {

	public static final String ACTION_BIND_SERVICE = "com.oasisfeng.island.action.BIND_SERVICE";
	static final String EXTRA_INTENT = "extra";
	static final String EXTRA_SERVICE_CONNECTION = "svc_conn";
	static final String EXTRA_FLAGS = "flags";

	private static final int SHUTTLE_ACTIVITY_START_FLAGS = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK
			| FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NO_ANIMATION | FLAG_ACTIVITY_NO_HISTORY;
	private static final long SHUTTLED_SERVICE_DISCONNECTION_DELAY = 5_000;	// Delay before actual disconnection from shuttled service

	static boolean bindServiceViaShuttle(final Context context, final Intent service, final ShuttleServiceConnection conn, final int flags) {
		if (sPendingUnbind.remove(conn)) {		// Reuse the still connected service, which is pending disconnection.
			Log.d(TAG, "Reuse service: " + conn);
			sMainHandler.post(conn::callServiceConnected);
			return true;
		}

		final PackageManager pm = context.getPackageManager();
		final ResolveInfo resolve = pm.resolveService(service, PackageManager.GET_DISABLED_COMPONENTS);
		if (resolve == null) return false;		// Fail early by resolving the service intent before launching ServiceShuttleActivity.

		final Bundle extras = new Bundle();
		extras.putBinder(EXTRA_SERVICE_CONNECTION, conn.createDispatcher());
		final Intent intent = new Intent(ACTION_BIND_SERVICE).addFlags(SHUTTLE_ACTIVITY_START_FLAGS).putExtras(extras)
				.putExtra(EXTRA_INTENT, service).putExtra(EXTRA_FLAGS, flags);
		Log.d(TAG, "Connecting to service in profile (via shuttle): " + service + " from " + conn);
		if (sForwarderComponent == null) try {
			sForwarderComponent = StreamSupport.stream(pm.queryIntentActivities(intent, 0))
					.filter((@NonNull ResolveInfo r) -> (r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
					.map((@NonNull ResolveInfo r) -> new ComponentName(r.activityInfo.packageName, r.activityInfo.name)).findFirst().orElse(null);
			if (sForwarderComponent == null) {
				Analytics.$().event("shuttle_service_forwarder_unavailable").send();
				return false;	// DO NOT fallback to default component assumed, due to cross-profile intent filter may not properly created.
			}
		} catch (final RuntimeException e) {
			Analytics.$().logAndReport(TAG, "Error querying " + intent, e);
		}
		if (sForwarderComponent == null)	// Last resort, assuming the default component unaltered.
			sForwarderComponent = new ComponentName("android", "com.android.internal.app.IntentForwarderActivity");

		intent.setComponent(sForwarderComponent);
		final Activity activity = Activities.findActivityFrom(context);
		try {
			if (activity != null) {
				activity.overridePendingTransition(0, 0);
				activity.startActivity(intent);
			} else context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK));
			return true;
		} catch (final ActivityNotFoundException e) {
			Analytics.$().logAndReport(TAG, "Error starting " + intent, e);
			return false;		// ServiceShuttle not ready in managed profile
		}
	}

	static void unbindShuttledServiceDelayed(final ShuttleServiceConnection conn) {
		Log.v(TAG, "Schedule service unbinding: " + conn);
		sPendingUnbind.add(conn);
		sMainHandler.removeCallbacks(sDelayedUnbindAll);
		sMainHandler.postDelayed(sDelayedUnbindAll, SHUTTLED_SERVICE_DISCONNECTION_DELAY);
	}

	private static void unbindPendingShuttledServices() {
		synchronized (sPendingUnbind) {
			for (final ShuttleServiceConnection conn : sPendingUnbind) try {
				Log.d(TAG, "Unbind service: " + conn);
				final boolean result = conn.unbind();
				if (! result) Log.w(TAG, "Remote service died before unbinding: " + conn);
			} catch (final RuntimeException e) {
				Log.e(TAG, "Error unbinding" + conn, e);
			}
			sPendingUnbind.clear();
		}
	}

	private static final Set<ShuttleServiceConnection> sPendingUnbind = Collections.synchronizedSet(new HashSet<>());
	private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
	private static final Runnable sDelayedUnbindAll = ServiceShuttle::unbindPendingShuttledServices;
	private static ComponentName sForwarderComponent;

	private static final String TAG = "Shuttle";
}
