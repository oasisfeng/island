package com.oasisfeng.island.api;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;

/**
 * Broadcast API invocation handler. Result will be delivered as broadcast result in ordered broadcast.
 *
 * Created by Oasis on 2017/9/18.
 */
public class ApiReceiver extends BroadcastReceiver {

	@Override public void onReceive(final Context context, final Intent intent) {
		Log.i(TAG, "API request: " + intent.toUri(0));
		if (intent.getComponent() != null) Log.w(TAG, "Never use explicit component name in API intent, use Intent.setPackage() instead.");
		if ((intent.getFlags() & FLAG_RECEIVER_FOREGROUND) == 0) Log.w(TAG, "Add Intent.FLAG_RECEIVER_FOREGROUND to API intent for less delay.");

		String result = ApiDispatcher.verifyCaller(context, intent, null);
		if (result != null) {
			setResult(Api.latest.RESULT_UNVERIFIED_IDENTITY, result, null);
			Log.w(TAG, "Caller verification failure: " + result);
			return;
		}
		result = ApiDispatcher.dispatch(context, intent);
		if (result == null) {
			setResult(Activity.RESULT_OK, null, null);
			Log.i(TAG, "API result: Success");
		} else {
			setResult(Activity.RESULT_CANCELED, result, null);
			Log.i(TAG, "API result: " + result);
		}
	}

	private static final String TAG = "API.Receiver";
}
