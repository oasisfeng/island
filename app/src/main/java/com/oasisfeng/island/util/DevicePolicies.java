package com.oasisfeng.island.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;

import java.util.List;

import static android.app.admin.DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED;

/**
 * Utility to ease the use of {@link android.app.admin.DevicePolicyManager}
 *
 * Created by Oasis on 2016/6/14.
 */
public class DevicePolicies {

	public DevicePolicies(final Context context) {
		mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		getDeviceAdminComponent(context);
	}

	/** @see DevicePolicyManager#addCrossProfileIntentFilter(ComponentName, IntentFilter, int) */
	public void addCrossProfileIntentFilter(final IntentFilter filter, final int flags) {
		mDevicePolicyManager.addCrossProfileIntentFilter(sCachedComponent, filter, flags);
	}

	/** @see DevicePolicyManager#enableSystemApp(ComponentName, String) */
	public void enableSystemApp(final String pkg) {
		mDevicePolicyManager.enableSystemApp(sCachedComponent, pkg);
	}

	/** @see DevicePolicyManager#enableSystemApp(ComponentName, Intent) */
	public int enableSystemApp(final Intent intent) {
		return mDevicePolicyManager.enableSystemApp(sCachedComponent, intent);
	}

	/** @see DevicePolicyManager#setApplicationHidden(ComponentName, String, boolean) */
	public boolean setApplicationHidden(final String pkg, final boolean hidden) {
		return mDevicePolicyManager.setApplicationHidden(sCachedComponent, pkg, hidden);
	}

	/** @see DevicePolicyManager#isApplicationHidden(ComponentName, String) */
	public boolean isApplicationHidden(final String pkg) {
		return mDevicePolicyManager.isApplicationHidden(sCachedComponent, pkg);
	}

	/** @see DevicePolicyManager#isAdminActive(ComponentName) */
	public boolean isAdminActive() {
		return mDevicePolicyManager.isAdminActive(sCachedComponent);
	}

	/** @see DevicePolicyManager#setSecureSetting(ComponentName, String, String) */
	public void setSecureSetting(final String setting, final String value) {
		mDevicePolicyManager.setSecureSetting(sCachedComponent, setting, value);
	}

	/** @see DevicePolicyManager#clearUserRestriction(ComponentName, String) */
	public void clearUserRestriction(final String key) {
		mDevicePolicyManager.clearUserRestriction(sCachedComponent, key);
	}

	/** @see DevicePolicyManager#setProfileName(ComponentName, String) */
	public void setProfileName(final String name) {
		mDevicePolicyManager.setProfileName(sCachedComponent, name);
	}

	/** @see DevicePolicyManager#setProfileEnabled(ComponentName) */
	public void setProfileEnabled() {
		mDevicePolicyManager.setProfileEnabled(sCachedComponent);
	}

	public DevicePolicyManager getManager() { return mDevicePolicyManager; }

	private static ComponentName getDeviceAdminComponent(final Context context) {
		if (sCachedComponent != null) return sCachedComponent;
		return sCachedComponent = detectDeviceAdminComponent(context);
	}

	private static ComponentName detectDeviceAdminComponent(final Context context) {
		final List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(
				new Intent(ACTION_DEVICE_ADMIN_ENABLED).setPackage(context.getPackageName()), 0);
		if (receivers.isEmpty()) throw new IllegalStateException("No device admin component detected");
		if (receivers.size() > 1) throw new IllegalStateException("Multiple device admin components detected");
		final ActivityInfo receiver = receivers.get(0).activityInfo;
		return new ComponentName(receiver.packageName, receiver.name);
	}

	private final DevicePolicyManager mDevicePolicyManager;

	private static ComponentName sCachedComponent;
}
