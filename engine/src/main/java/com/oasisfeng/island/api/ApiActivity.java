package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.oasisfeng.android.os.UserHandles;
import com.oasisfeng.island.util.Hacks;

/**
 * API via activity (better for crossing the user border)
 *
 * Created by Oasis on 2016/6/16.
 */
public class ApiActivity extends Activity {

	private void onIntent(final Intent intent) {
		Log.i(TAG, "API request: " + intent.toUri(0));
		if (intent.getAction() != null) {
			String result;
			final ComponentName caller = getCallingActivity();
			if (caller != null) try { @SuppressLint("WrongConstant")		// Invoked with startActivityForResult()
				final ActivityInfo info = getPackageManager().getActivityInfo(caller, Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED);
				int uid = info.applicationInfo.uid;
				if (UserHandles.getUserId(uid) != 0) {
					final String[] potential_pkgs = getPackageManager().getPackagesForUid(uid);
					if (potential_pkgs == null) uid = UserHandles.getAppId(uid);	// Fix the incorrect UID when MATCH_ANY_USER is used.
				}
				result = ApiDispatcher.verifyCaller(this, intent, caller.getPackageName(), uid);
			} catch (final PackageManager.NameNotFoundException e) {
				result = "Unverifiable caller activity: " + caller.flattenToShortString();
			} else result = ApiDispatcher.verifyCaller(this, intent, null, - 1);

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
