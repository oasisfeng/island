package com.oasisfeng.island.shuttle;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Activity interaction between owner user and profiles.
 *
 * Created by Oasis on 2017/7/4.
 */
public class ActivityShuttle extends BroadcastReceiver {

	@RequiresPermission(INTERACT_ACROSS_USERS)
	public static void startActivityAsUser(final Context context, final Intent intent, final UserHandle user) {
		final Intent shuttle_intent = new Intent();
		shuttle_intent.setSelector(intent);
		Log.d(TAG, "Shuttling activity: " + intent);
		context.sendBroadcastAsUser(shuttle_intent.setComponent(new ComponentName(context, ActivityShuttle.class)), user);
	}

	@Override public void onReceive(final Context context, final Intent intent) {
		if (intent.getComponent() == null) throw new IllegalArgumentException("Intent must be explicit: " + intent);
		final Intent activity_intent = intent.getSelector();
		Log.d(TAG, "Starting activity: " + activity_intent);
		context.startActivity(activity_intent);
	}

	private static final String TAG = "ActivityShuttle";
}
