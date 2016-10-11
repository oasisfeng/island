package com.oasisfeng.island.engine;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.util.DevicePolicies;

import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.Intent.EXTRA_INTENT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

/**
 * Forward the back-installation request to owner user.
 *
 * Created by Oasis on 2016/5/3.
 */
public class ActivityForwarder extends Activity {

	@Deprecated private static final String ACTION_FORWARD_ACTIVITY = "com.oasisfeng.island.action.FORWARD_ACTIVITY";

	static void startActivityForResultAsOwner(final Activity activity, final DevicePolicies dp, final Intent intent, final int request_code) {
		if (! GlobalStatus.running_in_owner) {
			prepare(activity, dp);
			activity.startActivityForResult(new Intent(ACTION_FORWARD_ACTIVITY).putExtra(EXTRA_INTENT, intent), request_code);
		} else activity.startActivityForResult(intent, request_code);
	}

	static void startActivityAsOwner(final Context context, final DevicePolicies dp, final Intent intent) {
		prepare(context, dp);
		context.startActivity(new Intent(ACTION_FORWARD_ACTIVITY).putExtra(EXTRA_INTENT, intent));
	}

	private static void prepare(final Context context, final DevicePolicies dp) {
		if (GlobalStatus.running_in_owner) return;
		// Disable installer in managed profile
		final ComponentName installer = new ComponentName(context, ActivityForwarder.class);
		context.getPackageManager().setComponentEnabledSetting(installer, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		// Ensure the intent filter is allowed to forward
		dp.addCrossProfileIntentFilter(new IntentFilter(ACTION_FORWARD_ACTIVITY), FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent(), payload;
		Log.d("Forwarder", "Intent: " + intent);
		if (ACTION_FORWARD_ACTIVITY.equals(intent.getAction()) && (payload = intent.getParcelableExtra(EXTRA_INTENT)) != null)
			try {
				startActivityForResult(payload, 0);
			} catch (final ActivityNotFoundException ignored) {}
		finish();
	}
}
