package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.oasisfeng.android.content.pm.LauncherAppsCompat;
import com.oasisfeng.android.os.UserHandles;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.appops.AppOpsHelper;

import java.util.Objects;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java9.util.Optional;
import java9.util.function.BiConsumer;
import java9.util.function.BiFunction;

import static android.content.Context.USER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;
import static android.os.Build.VERSION_CODES.O_MR1;
import static com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS;

/**
 * Utility to ease the use of {@link android.app.admin.DevicePolicyManager}
 *
 * Created by Oasis on 2016/6/14.
 */
public class DevicePolicies {

	public static final String ACTION_PACKAGE_UNFROZEN = "com.oasisfeng.island.action.PACKAGE_UNFROZEN";

	public boolean isProfileOwner() {
		return mDevicePolicyManager.isProfileOwnerApp(Modules.MODULE_ENGINE);
	}

	public static boolean isProfileOwner(final Context context, final UserHandle profile) {
		if (SDK_INT > O_MR1) return new DevicePolicies(context, profile).isProfileOwner();
		final Optional<ComponentName> profile_owner = getProfileOwnerAsUser(context, Users.toId(profile));
		return profile_owner != null && profile_owner.isPresent() && Modules.MODULE_ENGINE.equals(profile_owner.get().getPackageName());
	}

	public static @Nullable Optional<ComponentName> getProfileOwnerAsUser(final Context context, final UserHandle profile) {
		if (SDK_INT <= O_MR1) return getProfileOwnerAsUser(context, UserHandles.getIdentifier(profile));
		if (Hacks.DevicePolicyManager_getProfileOwner.isAbsent()) return null;
		try {
			return Optional.ofNullable(Hacks.DevicePolicyManager_getProfileOwner.invoke().on(new DevicePolicies(context, profile).mDevicePolicyManager));
		} catch (final RuntimeException e) {	// IllegalArgumentException("Requested profile owner for invalid userId", re) on API 21~23
			return null;						//   or RuntimeException by RemoteException.rethrowFromSystemServer() on API 24+
		}
	}

	/** @return the profile owner component (may not be present), or null for failure */
	private static @Nullable Optional<ComponentName> getProfileOwnerAsUser(final Context context, final int profile_id) {
		if (Hacks.DevicePolicyManager_getProfileOwnerAsUser.isAbsent()) return null;
		final DevicePolicyManager dpm = Objects.requireNonNull((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE));
		try {
			return Optional.ofNullable(Hacks.DevicePolicyManager_getProfileOwnerAsUser.invoke(profile_id).on(dpm));
		} catch (final RuntimeException e) {	// IllegalArgumentException("Requested profile owner for invalid userId", re) on API 21~23
			return null;						//   or RuntimeException by RemoteException.rethrowFromSystemServer() on API 24+
		}
	}

	@OwnerUser public boolean isActiveDeviceOwner() {
		return mDevicePolicyManager.isAdminActive(sCachedComponent) && mDevicePolicyManager.isDeviceOwnerApp(sCachedComponent.getPackageName());
	}

	/** @return the package name of current device owner, null if none or empty string if unknown. */
	@OwnerUser public String getDeviceOwner() {
		if (! Hacks.DevicePolicyManager_getDeviceOwner.isAbsent()) {
			return Hacks.DevicePolicyManager_getDeviceOwner.invoke().on(getManager());
		} else if (isActiveDeviceOwner()) return sCachedComponent.getPackageName();        // Fall-back check, only if we are the device owner.
		else return "";
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

	public void addUserRestrictionIfNeeded(final Context context, final String key) {
		if (Users.isProfileManagedByIsland() && UserManager.DISALLOW_SET_WALLPAPER.equals(key)) return;		// Immutable
		if (SDK_INT >= N) {
			if (! mDevicePolicyManager.getUserRestrictions(sCachedComponent).containsKey(key))
				mDevicePolicyManager.addUserRestriction(sCachedComponent, key);
		} else {
			final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
			if (um == null || ! um.hasUserRestriction(key))
				mDevicePolicyManager.addUserRestriction(sCachedComponent, key);
		}
	}

	public void clearUserRestrictionsIfNeeded(final Context context, final String... keys) {
		Bundle restrictions = null;
		for (final String key : keys) {
			if (Users.isProfileManagedByIsland() && UserManager.DISALLOW_SET_WALLPAPER.equals(key)) return;	// Immutable
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

	/** @see DevicePolicyManager#isBackupServiceEnabled(ComponentName) */
	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public boolean isBackupServiceEnabled() { return mDevicePolicyManager.isBackupServiceEnabled(sCachedComponent); }

	/** @see DevicePolicyManager#setBackupServiceEnabled(ComponentName, boolean) */
	@RequiresApi(N_MR1) @SuppressLint("NewApi") // Hidden on Android 7.1.x
	public void setBackupServiceEnabled(final boolean enabled) { mDevicePolicyManager.setBackupServiceEnabled(sCachedComponent, enabled); }

	/** @see DevicePolicyManager#isPackageSuspended(ComponentName, String) */
	@RequiresApi(N) public boolean isPackageSuspended(final String pkg) throws PackageManager.NameNotFoundException {	// Helper due to exception
		return mDevicePolicyManager.isPackageSuspended(sCachedComponent, pkg);
	}

	/** @see DevicePolicyManager#addCrossProfileIntentFilter(ComponentName, IntentFilter, int) */
	public void addCrossProfileIntentFilter(final IntentFilter filter, final int flags) {	// Need this helper since IntentFilters may throws.
		mDevicePolicyManager.addCrossProfileIntentFilter(sCachedComponent, filter, flags);
	}

	public DevicePolicyManager getManager() { return mDevicePolicyManager; }

	private static void cacheDeviceAdminComponent(final Context context) {
		if (sCachedComponent == null) sCachedComponent = DeviceAdmins.getComponentName(context);
	}

	public boolean setApplicationHidden(final String pkg, final boolean hidden) {
		if (SDK_INT > O_MR1 && hidden && Permissions.has(mAppContext, GET_APP_OPS_STATS)) try {
			AppOpsHelper.saveAppOps(mAppContext, pkg);
		} catch (final PackageManager.NameNotFoundException | RuntimeException e) {
			Analytics.$().logAndReport(TAG, "Error saving app ops settings for " + pkg, e);
		}
		final boolean changed = setApplicationHiddenWithoutAppOpsSaver(pkg, hidden);

		if (changed && SDK_INT > O_MR1 && ! hidden) try {
			AppOpsHelper.restoreAppOps(mAppContext, pkg);
		} catch (final PackageManager.NameNotFoundException | RuntimeException e) {
			Analytics.$().logAndReport(TAG, "Error restoring app ops settings for " + pkg, e);
		}
		return changed;
	}

	public boolean setApplicationHiddenWithoutAppOpsSaver(final String pkg, final boolean hidden) {
		final boolean changed = mDevicePolicyManager.setApplicationHidden(sCachedComponent, pkg, hidden);
		if (changed && ! hidden) Modules.broadcast(mAppContext, new Intent(ACTION_PACKAGE_UNFROZEN, Uri.fromParts("package", pkg, null)));
		return changed;
	}

	public void setUserRestriction(final String restriction, final boolean enabled) {
		execute(enabled ? DevicePolicyManager::addUserRestriction : DevicePolicyManager::clearUserRestriction, restriction);
	}

	public DevicePolicies(final Context context) {
		mAppContext = context.getApplicationContext();
		mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		cacheDeviceAdminComponent(context);
	}

	@RequiresApi(O_MR1 + 1) private DevicePolicies(final Context context, final UserHandle profile) {
		ApplicationInfo profile_app_info = null;
		if (SDK_INT >= N) profile_app_info = new LauncherAppsCompat(context).getApplicationInfoNoThrows(Modules.MODULE_ENGINE, 0, profile);
		if (profile_app_info == null) {		// Make up the required profile ApplicationInfo
			profile_app_info = context.getApplicationInfo();
			profile_app_info.uid = UserHandles.getUid(UserHandles.getIdentifier(profile), UserHandles.getAppId(Process.myUid()));
		}
		try {
			mAppContext = Hacks.Context_createApplicationContext.invoke(profile_app_info, 0).on(context);
		} catch (final PackageManager.NameNotFoundException e) { throw new IllegalStateException(e); }	// Should never happen
		mDevicePolicyManager = (DevicePolicyManager) mAppContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
		cacheDeviceAdminComponent(context);
	}

	/* Helpers for more APIs in DevicePolicyManager */

	public interface TriConsumer<A, B, C> { void accept(A a, B b, C c); }
	public interface TriFunction<A, B, C, R> { R apply(A a, B b, C c); }
	public interface QuadConsumer<A, B, C, D> { void accept(A a, B b, C c, D d); }
	public interface QuadFunction<A, B, C, D, R> { R apply(A a, B b, C c, D d); }
	public interface QuinFunction<A, B, C, D, E, R> { R apply(A a, B b, C c, D d, E e); }

	public void execute(final BiConsumer<DevicePolicyManager, ComponentName> callee) {
		callee.accept(mDevicePolicyManager, sCachedComponent);
	}
	public <A> void execute(final TriConsumer<DevicePolicyManager, ComponentName, A> callee, final A a) {
		callee.accept(mDevicePolicyManager, sCachedComponent, a);
	}
	public <A, B> void execute(final QuadConsumer<DevicePolicyManager, ComponentName, A, B> callee, final A a, final B b) {
		callee.accept(mDevicePolicyManager, sCachedComponent, a, b);
	}
	public <R> R invoke(final BiFunction<DevicePolicyManager, ComponentName, R> callee) {
		return callee.apply(mDevicePolicyManager, sCachedComponent);
	}
	public <A, R> R invoke(final TriFunction<DevicePolicyManager, ComponentName, A, R> callee, final A a) {
		return callee.apply(mDevicePolicyManager, sCachedComponent, a);
	}
	public <A, B, R> R invoke(final QuadFunction<DevicePolicyManager, ComponentName, A, B, R> callee, final A a, final B b) {
		return callee.apply(mDevicePolicyManager, sCachedComponent, a, b);
	}
	public <A, B, C, R> R invoke(final QuinFunction<DevicePolicyManager, ComponentName, A, B, C, R> callee, final A a, final B b, final C c) {
		return callee.apply(mDevicePolicyManager, sCachedComponent, a, b, c);
	}

	private final Context mAppContext;
	private final DevicePolicyManager mDevicePolicyManager;

	private static ComponentName sCachedComponent;
	private static final String TAG = DevicePolicies.class.getSimpleName();
}
