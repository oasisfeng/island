package com.oasisfeng.island.shuttle;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.oasisfeng.island.api.Api;
import com.oasisfeng.island.api.ApiReceiver;
import com.oasisfeng.island.util.Users;

import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Activity interaction between owner user and profiles.
 *
 * Created by Oasis on 2017/7/4.
 */
public abstract class ActivityShuttle extends BroadcastReceiver {

	@RequiresPermission(INTERACT_ACROSS_USERS)
	public static void startActivityAsUser(final Context context, final Intent intent, final UserHandle user) {
		Log.d(TAG, "Shuttle to activity in user " + Users.toId(user) + ": " + intent);
		final Intent shuttle_intent = buildShuttleActivityIntent(context, intent, user);
		context.sendBroadcastAsUser(shuttle_intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND/* less delay */), user);
	}

	public static Intent buildShuttleActivityIntent(final Context context, final Intent intent, final @Nullable UserHandle user) {
		final PendingIntent caller_id = PendingIntent.getBroadcast(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
		final Intent shuttle_intent = new Intent(Api.latest.ACTION_LAUNCH).setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
				.setComponent(new ComponentName(context, ApiReceiver.class)).putExtra(Api.latest.EXTRA_CALLER_ID, caller_id);
		if (user != null) shuttle_intent.putExtra(Intent.EXTRA_USER, user);
		return shuttle_intent;
	}

	private static final String TAG = "ActivityShuttle";
}
