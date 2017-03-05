package com.oasisfeng.island.engine;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import java.lang.reflect.Proxy;
import java.util.List;

import static android.content.Context.DEVICE_POLICY_SERVICE;
import static android.content.Context.USER_SERVICE;

/**
 * Controller of Island
 *
 * Created by Oasis on 2017/2/20.
 */
public class IslandManager {

	public static final int CLONE_RESULT_ALREADY_CLONED = 0;
	public static final int CLONE_RESULT_OK_INSTALL = 1;
	public static final int CLONE_RESULT_OK_SYS_APP = 2;
	public static final int CLONE_RESULT_OK_GOOGLE_PLAY = 10;
	public static final int CLONE_RESULT_UNKNOWN_SYS_MARKET = 11;
	public static final int CLONE_RESULT_NOT_FOUND = -1;
	public static final int CLONE_RESULT_NO_SYS_MARKET = -2;

	public static final IIslandManager NULL = (IIslandManager) Proxy.newProxyInstance(IIslandManager.class.getClassLoader(), new Class[] {IIslandManager.class},
			(proxy, method, args) -> { throw new RemoteException("Not connected yet"); });

	public static boolean launchApp(final Context context, final String pkg, final UserHandle profile) {
		final LauncherApps launcher_apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		final List<LauncherActivityInfo> activities = launcher_apps.getActivityList(pkg, profile);
		if (activities == null || activities.isEmpty()) return false;
		launcher_apps.startMainActivity(activities.get(0).getComponentName(), profile, null, null);
		return true;
	}

	public void deactivateDeviceOwner() {
		Analytics.$().event("action_deactivate").send();
		mDevicePolicies.clearCrossProfileIntentFilters();
		mDevicePolicies.getManager().clearDeviceOwnerApp(mContext.getPackageName());
		try {
			mDevicePolicies.removeActiveAdmin();			// Since Android 7.1, clearDeviceOwnerApp() itself does remove active device-admin,
		} catch (final SecurityException ignored) {}		//   thus SecurityException will be thrown here.
		final Activity activity = Activities.findActivityFrom(mContext);
		if (activity != null) activity.finish();
	}

	public boolean isDeviceOwner() {
		return mDevicePolicies.getManager().isDeviceOwnerApp(Modules.MODULE_ENGINE);
	}

	public static boolean isProfileOwner(final Context context) {
		final UserHandle profile = getManagedProfile(context);
		if (profile == null) return false;
		final ComponentName profile_owner = getProfileOwner(context, profile);
		return profile_owner != null && context.getPackageName().equals(profile_owner.getPackageName());
	}

	public boolean isProfileOwnerActive() {
		if (Users.isOwner(Process.myUserHandle())) throw new IllegalStateException("Must not be called in owner user");
		return mDevicePolicies.isAdminActive();
	}

	public static @Nullable UserHandle getManagedProfile(final Context context) {
		final UserManager um = (UserManager) context.getSystemService(USER_SERVICE);
		final List<UserHandle> profiles = um.getUserProfiles();
		final UserHandle current_user = Process.myUserHandle();
		for (final UserHandle profile : profiles)
			if (! profile.equals(current_user)) return profile;   	// Only one managed profile is supported by Android at present.
		return null;
	}

	/** @return the profile owner component, null for none or failure */
	public static @Nullable ComponentName getProfileOwner(final Context context, final @NonNull UserHandle profile) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
		try {
			return Hacks.DevicePolicyManager_getProfileOwnerAsUser.invoke(Users.toId(profile)).on(dpm);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	public IslandManager(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;
}
