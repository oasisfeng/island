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
		if ((intent.getFlags() & FLAG_RECEIVER_FOREGROUND) == 0) Log.w(TAG, "Add Intent.FLAG_RECEIVER_FOREGROUND to API intent for less delay.");

		String result = ApiDispatcher.verifyCaller(context, intent, null, -1);
		if (result != null) {
			setResultIfOrdered(Api.latest.RESULT_UNVERIFIED_IDENTITY, result);
			Log.w(TAG, "Caller verification failure: " + result);
			return;
		}
		result = ApiDispatcher.dispatch(context, intent);
		if (result == null) {
			setResultIfOrdered(Activity.RESULT_OK, null);
			Log.i(TAG, "API result: Success");
		} else {
			setResultIfOrdered(Activity.RESULT_CANCELED, result);
			Log.i(TAG, "API result: " + result);
		}
	}

	private void setResultIfOrdered(final int result, final String message) {
		if (isOrderedBroadcast()) setResult(result, message, null);
	}

	private static final String TAG = "API.Receiver";
}
