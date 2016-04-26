package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.oasisfeng.island.MainActivity;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;

import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;

/**
 * The one-time provisioning for newly created managed profile of Island
 *
 * Created by Oasis on 2016/4/26.
 */
public class IslandProvisioning {

	/** Provision state: 1 - Managed profile provision is completed, 2 - Island provision is started, 3 - Island provision is completed */
	private static final String PREF_KEY_PROVISION_STATE = "provision.state";

	@SuppressLint("CommitPrefEdits") public void onProfileProvisioningComplete() {
		Log.d(TAG, "onProfileProvisioningComplete");
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 1).commit();
		startProfileOwnerIslandProvisioning();
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();
		MainActivity.startAsUser(mContext, android.os.Process.myUserHandle());
	}

	@SuppressLint("CommitPrefEdits") public void startProfileOwnerProvisioningIfNeeded() {
		if (mIslandManager.isDeviceOwner()) return;	// Do nothing for device owner
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		final int state = prefs.getInt(PREF_KEY_PROVISION_STATE, 0);
		if (state >= 3) return;		// Already provisioned
		if (state == 2) {
			Log.w(TAG, "Last provision attempt failed, no more attempts...");
			return;		// Last attempt failed again, no more attempts.
		} else if (state == 1) Log.w(TAG, "Last provision attempt might be interrupted, try provisioning one more time...");
		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 2).commit();	// Avoid further attempts

		if (state == 0)		// Managed profile provision was not performed, the profile may be enabled manually.
			ProfileOwnerSystemProvisioning.start(mIslandManager);	// Simulate the stock managed profile provision

		startProfileOwnerIslandProvisioning();	// Last provision attempt may be interrupted

		prefs.edit().putInt(PREF_KEY_PROVISION_STATE, 3).commit();
	}

	private void startProfileOwnerIslandProvisioning() {
		new SystemAppsManager(mContext, mIslandManager).prepareSystemApps();
		mIslandManager.enableProfile();
		enableAdditionalForwarding();
	}

	private void enableAdditionalForwarding() {
		// For sharing across Island (bidirectional)
		mIslandManager.enableForwarding(new IntentFilter(Intent.ACTION_SEND), FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
		// For web browser
		final IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		browser.addDataScheme("http"); browser.addDataScheme("https"); browser.addDataScheme("ftp");
		mIslandManager.enableForwarding(browser, FLAG_PARENT_CAN_ACCESS_MANAGED);
	}

	public IslandProvisioning(final Context context) {
		mContext = context;
		mIslandManager = new IslandManager(context);
	}

	public IslandProvisioning(final Context context, final IslandManager island) {
		mContext = context;
		mIslandManager = island;
	}

	private final Context mContext;
	private final IslandManager mIslandManager;

	private static final String TAG = "Island.Provision";
}
