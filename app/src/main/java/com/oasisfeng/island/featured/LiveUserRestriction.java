package com.oasisfeng.island.featured;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;

import com.oasisfeng.island.util.Users;

import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * {@link LiveData} for user restriction.
 *
 * Created by Oasis on 2019-1-19.
 */
public class LiveUserRestriction extends LiveData<Boolean> {

	private static final String ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED = "android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED";	// DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED

	LiveUserRestriction(final Context context, final String restriction, final UserHandle user) {
		mAppContext = context.getApplicationContext();
		mRestriction = restriction;
		mUser = user;
	}

	boolean query(final Context context) {
		final Bundle restrictions = ((UserManager) context.getSystemService(Context.USER_SERVICE)).getUserRestrictions(mUser);
		return restrictions.containsKey(mRestriction);
	}

	/** Request explicit update, in case broadcast receiver does not work (e.g. across users) */
	static void requestUpdate(final Context context) {
		LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED));
	}

	@Override protected void onActive() {
		if (mHandler.hasMessages(0)) {	// Skip successive onActive() immediately after onInactive().
			mHandler.removeCallbacksAndMessages(null);
			return;
		}
		final IntentFilter filter = new IntentFilter(ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
		if (Users.current().equals(mUser)) mAppContext.registerReceiver(mReceiver, filter);
		LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mReceiver, filter);

		update(mAppContext);
	}

	@Override protected void onInactive() {
		mHandler.post(this::deactivate); // Workaround for successive onActive(), due to lack of equality checking in LiveDataListener.setLifecycleOwner()
	}

	private void deactivate() {
		LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(mReceiver);
		if (Users.current().equals(mUser)) mAppContext.unregisterReceiver(mReceiver);
	}

	private void update(final Context context) {
		setValue(query(context));
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent i) {
		update(context);
	}};

	private final Context mAppContext;
	private final Handler mHandler = new Handler();
	private final String mRestriction;
	private final UserHandle mUser;
}
