package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.List;

import java9.util.Optional;

import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.USER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;

/**
 * Utility to ease the use of {@link android.app.admin.DevicePolicyManager}
 *
 * Created by Oasis on 2016/6/14.
 */
public class DevicePolicies {

	/** @return whether Island is the profile owner, absent if no enabled profile or profile has no owner, or null for failure. */
	public static @Nullable Optional<Boolean> isProfileOwner(final Context context) {	// TODO: Rename to isEnabledProfileOwner()
		final UserHandle profile = getManagedProfile(context);
		if (profile == null) return Optional.empty();
		return isProfileOwner(context, profile);
	}

	/** @return whether Island is the profile owner, absent if no such profile or profile has no owner, or null for failure. */
	public static @Nullable Optional<Boolean> isProfileOwner(final Context context, final UserHandle profile) {
		final Optional<ComponentName> profile_owner = getProfileOwnerAsUser(context, Users.toId(profile));
		return profile_owner == null ? null : ! profile_owner.isPresent() ? Optional.empty()
				: Optional.of(Modules.MODULE_ENGINE.equals(profile_owner.get().getPackageName()));
	}

	public static @Nullable UserHandle getManagedProfile(final Context context) {
		final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
		if (um == null) return null;
		final List<UserHandle> profiles = um.getUserProfiles();
		final UserHandle current_user = Process.myUserHandle();
		for (final UserHandle profile : profiles)
			if (! profile.equals(current_user)) return profile;   	// Only one managed profile is supported by Android at present.
		return null;
	}

	/** @return the profile owner component (may not be present), or null for failure */
	public static @Nullable Optional<ComponentName> getProfileOwnerAsUser(final Context context, final int profile) {
		if (Hacks.DevicePolicyManager_getProfileOwnerAsUser.isAbsent()) return null;
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
		try {
			return Optional.ofNullable(Hacks.DevicePolicyManager_getProfileOwnerAsUser.invoke(profile).on(dpm));
		} catch (final RuntimeException e) {	// IllegalArgumentException("Requested profile owner for invalid userId", re) on API 21~23
			return null;						//   or RuntimeException by RemoteException.rethrowFromSystemServer() on API 24+
		}
	}

	/* Shortcuts for APIs in DevicePolicyManager */

	/** @see DevicePolicyManager#isDeviceOwnerApp(String) */
	public boolean isDeviceOwner() {
		return mDevicePolicyManager.isDeviceOwnerApp(sCachedComponent.getPackageName());
	}

	/** @return the package name of current device owner, null if none or empty string if unknown. */
	@OwnerUser public String getDeviceOwner() {
		if (! Hacks.DevicePolicyManager_getDeviceOwner.isAbsent()) {
			return Hacks.DevicePolicyManager_getDeviceOwner.invoke().on(getManager());
		} else if (isDeviceOwner()) return sCachedComponent.getPackageName();        // Fall-back check, only if we are the device owner.
		else return "";
	}

	/** @see DevicePolicyManager#addCrossProfileIntentFilter(ComponentName, IntentFilter, int) */
	public void addCrossProfileIntentFilter(final IntentFilter filter, final int flags) {
		mDevicePolicyManager.addCrossProfileIntentFilter(sCachedComponent, filter, flags);
	}

	public void clearCrossProfileIntentFilters() {
		mDevicePolicyManager.clearCrossProfileIntentFilters(sCachedComponent);
	}

	/** @return true if successfully enabled, false if package not found or not system app.
	 * @see DevicePolicyManager#enableSystemApp(ComponentName, String) */
	public boolean enableSystemApp(final String pkg) {
		try {
			mDevicePolicyManager.enableSystemApp(sCachedComponent, pkg);
			return true;
		} catch (final RuntimeException e) {
			// May throw NPE if package not found (on Android 5.x, see commit 637baaf0db76f9e1e51eeab077ffb85da0ff9308 in platform_frameworks_base)
			// 	 or IllegalArgumentException (on Android 6+) if package is not present on this device.
			if (e instanceof NullPointerException || e instanceof IllegalArgumentException) return false;
			throw e;
		}
	}

	/** @see DevicePolicyManager#enableSystemApp(ComponentName, Intent) */
	public boolean enableSystemApp(final Intent intent) {
		try {
			return mDevicePolicyManager.enableSystemApp(sCachedComponent, intent) > 0;
		} catch (final IllegalArgumentException e) {
			// This exception may be thrown on Android 5.x (but not 6.0+) if non-system apps also match this intent.
			// System apps should have been enabled before this exception is thrown, so we just ignore it.
			Log.w(TAG, "System apps may not be enabled for: " + intent);
			return true;
		}
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
	@RequiresApi(N) public String[] setPackagesSuspended(final String[] pkgs, final boolean suspended) {
		return mDevicePolicyManager.setPackagesSuspended(sCachedComponent, pkgs, suspended);
	}

	/**
	 * Called by device or profile owners to determine if a package is suspended.
	 *
	 * @param pkg The name of the package to retrieve the suspended status of.
	 */
	@RequiresApi(N) public boolean isPackageSuspended(final String pkg) throws NameNotFoundException {
		return mDevicePolicyManager.isPackageSuspended(sCachedComponent, pkg);
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

	public void addUserRestrictionIfNeeded(final Context context, final String key) {
		if (Users.isProfile() && UserManager.DISALLOW_SET_WALLPAPER.equals(key)) return;		// Immutable
		if (SDK_INT >= N) {
			if (! mDevicePolicyManager.getUserRestrictions(sCachedComponent).containsKey(key))
				mDevicePolicyManager.addUserRestriction(sCachedComponent, key);
		} else {
			final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
			if (um == null || ! um.hasUserRestriction(key))
				mDevicePolicyManager.addUserRestriction(sCachedComponent, key);
		}
	}

	/** @see DevicePolicyManager#clearUserRestriction(ComponentName, String) */
	public void clearUserRestriction(final String key) {
		mDevicePolicyManager.clearUserRestriction(sCachedComponent, key);
	}

	public void clearUserRestrictionsIfNeeded(final Context context, final String... keys) {
		Bundle restrictions = null;
		for (final String key : keys) {
			if (Users.isProfile() && UserManager.DISALLOW_SET_WALLPAPER.equals(key)) return;		// Immutable
			if (SDK_INT >= N) {
				if (restrictions == null) restrictions = mDevicePolicyManager.getUserRestrictions(sCachedComponent);
				if (restrictions.containsKey(key))
					mDevicePolicyManager.clearUserRestriction(sCachedComponent, key);
			} else {
				final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
				if (um == null || um.hasUserRestriction(key))
					mDevicePolicyManager.clearUserRestriction(sCachedComponent, key);
			}
		}
	}

	/** @see DevicePolicyManager#getUserRestrictions(ComponentName) */
	@RequiresApi(N) public Bundle getUserRestrictions() {
		return mDevicePolicyManager.getUserRestrictions(sCachedComponent);
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

	/** @see DevicePolicyManager#setPermissionGrantState(ComponentName, String, String, int) */
	@RequiresApi(M) public boolean setPermissionGrantState(final String pkg, final String permission, final int state) {
		return mDevicePolicyManager.setPermissionGrantState(sCachedComponent, pkg, permission, state);
	}

	/** @see DevicePolicyManager#getPermissionGrantState(ComponentName, String, String) */
	@RequiresApi(M) public int getPermissionGrantState(final String pkg, final String permission) {
		return mDevicePolicyManager.getPermissionGrantState(sCachedComponent, pkg, permission);
	}

	/** @see DevicePolicyManager#isBackupServiceEnabled(ComponentName) */
	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public boolean isBackupServiceEnabled() {
		return mDevicePolicyManager.isBackupServiceEnabled(sCachedComponent);
	}

	/** @see DevicePolicyManager#setBackupServiceEnabled(ComponentName, boolean) */
	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public void setBackupServiceEnabled(final boolean enabled) {
		mDevicePolicyManager.setBackupServiceEnabled(sCachedComponent, enabled);
	}

	public DevicePolicyManager getManager() { return mDevicePolicyManager; }

	private static void cacheDeviceAdminComponent(final Context context) {
		if (sCachedComponent == null) sCachedComponent = DeviceAdmins.getComponentName(context);
	}

	public DevicePolicies(final Context context) {
		mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		cacheDeviceAdminComponent(context);
	}

	private final DevicePolicyManager mDevicePolicyManager;

	private static ComponentName sCachedComponent;
	private static final String TAG = DevicePolicies.class.getSimpleName();
}
