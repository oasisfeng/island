package com.oasisfeng.island.api;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * API via activity (better for crossing the user border)
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	private void onIntent(final Intent intent) {
		Log.i(TAG, "API request: " + intent.toUri(0));
		if (intent.getAction() != null) {
			String result = ApiDispatcher.verifyCaller(this, intent, getCallingPackage());
			if (result == null) {
				result = ApiDispatcher.dispatch(this, intent);
				if (result == null) {
					setResult(Activity.RESULT_OK);
					Log.i(TAG, "API result: Success");
				} else {
					setResult(Activity.RESULT_CANCELED, new Intent(result));
					Log.i(TAG, "API result: " + result);
				}
			} else {
				setResult(Api.latest.RESULT_UNVERIFIED_IDENTITY, new Intent(result));
				Log.w(TAG, "Unverified client: " + result);
			}
		} else setResult(RESULT_CANCELED, new Intent("No action"));
		finish();
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		onIntent(getIntent());
	}

	@Override protected void onNewIntent(final Intent intent) {
		setIntent(intent);
		onIntent(intent);
	}

	private static final String TAG = "API.Activity";
}
