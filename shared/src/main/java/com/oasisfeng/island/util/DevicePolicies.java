package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import com.oasisfeng.hack.Hack;

import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;

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

	public boolean isProfileOwner() {
		return mDevicePolicyManager.isProfileOwnerApp(sCachedComponent.getPackageName());
	}

	public boolean isDeviceOwner() {
		return mDevicePolicyManager.isDeviceOwnerApp(sCachedComponent.getPackageName());
	}

	/** @see DevicePolicyManager#addCrossProfileIntentFilter(ComponentName, IntentFilter, int) */
	public void addCrossProfileIntentFilter(final IntentFilter filter, final int flags) {
		mDevicePolicyManager.addCrossProfileIntentFilter(sCachedComponent, filter, flags);
	}

	public void clearCrossProfileIntentFilters() {
		mDevicePolicyManager.clearCrossProfileIntentFilters(sCachedComponent);
	}

	/** @see DevicePolicyManager#enableSystemApp(ComponentName, String) */
	public void enableSystemApp(final String pkg) {
		mDevicePolicyManager.enableSystemApp(sCachedComponent, pkg);
	}

	/** @see DevicePolicyManager#enableSystemApp(ComponentName, Intent) */
	public int enableSystemApp(final Intent intent) {
		return mDevicePolicyManager.enableSystemApp(sCachedComponent, intent);
	}

	/** @return Whether the hidden setting of the package was successfully updated, (false if not found)
	 *  @see DevicePolicyManager#setApplicationHidden(ComponentName, String, boolean) */
	public boolean setApplicationHidden(final String pkg, final boolean hidden) {
		return mDevicePolicyManager.setApplicationHidden(sCachedComponent, pkg, hidden);
	}

	/** @return true if the package is hidden or not installed on device, false otherwise (including not installed in current user/profile).
	 *  @see DevicePolicyManager#isApplicationHidden(ComponentName, String) */
	public boolean isApplicationHidden(final String pkg) {
		return mDevicePolicyManager.isApplicationHidden(sCachedComponent, pkg);
	}

	/**
	 * Called by device or profile owners to suspend packages for this user.
	 * <p>A suspended package will not be able to start activities. Its notifications will be hidden, it will not show up in recents, will not be able to show toasts or dialogs or ring the device.
	 * <p>The package must already be installed.
	 *
	 * @param pkgs	The package names to suspend or unsuspend.
	 * @param suspended	If set to true than the packages will be suspended, if set to false the packages will be unsuspended.
	 * @return an array of package names for which the suspended status is not set as requested in this method.
	 */
	public String[] setPackagesSuspended(final String[] pkgs, final boolean suspended) {
		return Hack.into(DevicePolicyManager.class).method("setPackagesSuspended").returning(String[].class)
				.fallbackReturning(null).withParams(ComponentName.class, String[].class, boolean.class)
				.invoke(sCachedComponent, pkgs, suspended).on(mDevicePolicyManager);
	}

	/**
	 * Called by device or profile owners to determine if a package is suspended.
	 *
	 * @param pkg The name of the package to retrieve the suspended status of.
	 */
	public boolean isPackageSuspended(final String pkg) throws NameNotFoundException {
		return Hack.into(DevicePolicyManager.class).method("isPackageSuspended").throwing(NameNotFoundException.class)
				.returning(boolean.class).fallbackReturning(false).withParams(ComponentName.class, String.class)
				.invoke(sCachedComponent, pkg).on(mDevicePolicyManager);
	}

	/** @see DevicePolicyManager#isAdminActive(ComponentName) */
	public boolean isAdminActive() {
		return mDevicePolicyManager.isAdminActive(sCachedComponent);
	}

	/** @see DevicePolicyManager#setSecureSetting(ComponentName, String, String) */
	public void setSecureSetting(final String setting, final String value) {
		mDevicePolicyManager.setSecureSetting(sCachedComponent, setting, value);
	}

	/** @see DevicePolicyManager#addUserRestriction(ComponentName, String) */
	public void addUserRestriction(final String key) {
		mDevicePolicyManager.addUserRestriction(sCachedComponent, key);
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

	/** @see DevicePolicyManager#removeActiveAdmin(ComponentName) */
	public void removeActiveAdmin() {
		mDevicePolicyManager.removeActiveAdmin(sCachedComponent);
	}

	@RequiresApi(M) public boolean setPermissionGrantState(final String pkg, final String permission, final int state) {
		return mDevicePolicyManager.setPermissionGrantState(sCachedComponent, pkg, permission, state);
	}

	@RequiresApi(N) public Bundle getUserRestrictions() {
		return mDevicePolicyManager.getUserRestrictions(sCachedComponent);
	}

	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public boolean isBackupServiceEnabled() {
		return mDevicePolicyManager.isBackupServiceEnabled(sCachedComponent);
	}

	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public void setBackupServiceEnabled(final boolean enabled) {
		mDevicePolicyManager.setBackupServiceEnabled(sCachedComponent, enabled);
	}

	public DevicePolicyManager getManager() { return mDevicePolicyManager; }

	private static ComponentName getDeviceAdminComponent(final Context context) {
		if (sCachedComponent != null) return sCachedComponent;
		return sCachedComponent = DeviceAdmins.getComponentName(context);
	}

	private final DevicePolicyManager mDevicePolicyManager;

	private static ComponentName sCachedComponent;
}
