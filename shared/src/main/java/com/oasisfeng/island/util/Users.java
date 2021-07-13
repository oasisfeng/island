package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.Context.USER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N_MR1;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for user-related helpers. Only works within the process where this provider is declared to be running.
 *
 * Created by Oasis on 2016/9/25.
 */
public class Users extends PseudoContentProvider {

	public static @Nullable UserHandle profile;		// The first profile managed by Island (semi-immutable, until profile is created or destroyed)
	private static UserHandle mParentProfile;

	public static boolean hasProfile() { return profile != null; }
	public static UserHandle getParentProfile() { return mParentProfile; }

	private static final UserHandle CURRENT = Process.myUserHandle();
	private static final int CURRENT_ID = toId(CURRENT);

	public static UserHandle current() { return CURRENT; }
	public static int currentId() { return CURRENT_ID; }

	@Override public boolean onCreate() {
		Log.v(TAG, "onCreate()");
		final int priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1;
		@SuppressLint("InlinedApi") final String ACTION_PROFILE_OWNER_CHANGED = DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED;
		context().registerReceiver(mProfileChangeObserver, IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED,// ACTION_MANAGED_PROFILE_ADDED is sent by DevicePolicyManagerService.setProfileEnabled()
				Intent.ACTION_MANAGED_PROFILE_REMOVED, ACTION_PROFILE_OWNER_CHANGED).inPriority(priority));             // ACTION_PROFILE_OWNER_CHANGED is sent after "dpm set-profile-owner ..."
		refreshUsers(context());
		return true;
	}

	/** This method should not be called under normal circumstance. */
	public static void refreshUsers(final Context context) {
		mDebugBuild = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		final List<UserHandle> profiles = requireNonNull((UserManager) context.getSystemService(USER_SERVICE)).getUserProfiles();
		final List<UserHandle> profiles_managed_by_island = new ArrayList<>(profiles.size() - 1);
		mParentProfile = profiles.get(0);
		if (mParentProfile.equals(CURRENT)) {      // Running in parent profile

			final String ui_module = Modules.getMainLaunchActivity(context).getPackageName();
			final LauncherApps la = context.getSystemService(LauncherApps.class);
			final String activity_in_owner = la.getActivityList(ui_module, CURRENT).get(0).getName();
			for (int i = 1/* skip parent */; i < profiles.size(); i ++) {
				final UserHandle profile = profiles.get(i);
				for (final LauncherActivityInfo activity : la.getActivityList(ui_module, profile))
					if (! activity.getName().equals(activity_in_owner)) {   // Separate "Island Settings" launcher activity is enabled, only if profile is managed by Island.
						profiles_managed_by_island.add(profile);
						Log.i(TAG, "Profile managed by Island: " + toId(profile));
					} else Log.i(TAG, "Profile not managed by Island: " + toId(profile));
			}
		} else for (int i = 1/* skip parent */; i < profiles.size(); i ++) {
			final UserHandle user = profiles.get(i);
			if (user.equals(CURRENT)) {
				profiles_managed_by_island.add(user);
				Log.i(TAG, "Profile managed by Island: " + toId(user));
			} else Log.w(TAG, "Skip sibling profile (may not managed by Island): " + toId(user));
		}
		profile = profiles_managed_by_island.isEmpty() ? null : profiles_managed_by_island.get(0);
		sProfilesManagedByIsland = Collections.unmodifiableList(profiles_managed_by_island);
		try { sCurrentProfileManagedByIsland = new DevicePolicies(context).isProfileOwner(); }
		catch (final RuntimeException e) { Log.e(TAG, "Error checking current profile", e); }
	}

	public static boolean isProfileRunning(final Context context, final UserHandle user) {
		if (CURRENT.equals(user)) return true;
		final UserManager um = requireNonNull(context.getSystemService(UserManager.class));
		if (SDK_INT >= N_MR1) try {
			return um.isUserRunning(user);
		} catch (final RuntimeException e) {
			Log.w(TAG, "Error checking running state for user " + toId(user));
		}
		return um.isQuietModeEnabled(user);
	}

	// TODO: Support secondary user with managed profile.
	public static boolean isParentProfile() { return CURRENT_ID == toId(mParentProfile); }
	public static boolean isParentProfile(final UserHandle user) { return user.equals(mParentProfile); }
	public static boolean isParentProfile(final int user_id) { return user_id == toId(mParentProfile); }

	public static boolean isProfileManagedByIsland() { return sCurrentProfileManagedByIsland; }
	@OwnerUser public static boolean isProfileManagedByIsland(final UserHandle user) {
		ensureParentProfile();
		if (isParentProfile(user)) {
			if (isParentProfile()) return sCurrentProfileManagedByIsland;
			throw new IllegalArgumentException("Not working for profile parent user");
		}
		return sProfilesManagedByIsland.contains(user);
	}

	/** Excluding parent profile */
	@OwnerUser public static List<UserHandle> getProfilesManagedByIsland() {
		ensureParentProfile();
		return sProfilesManagedByIsland/* already unmodifiable */;
	}

	public static int toId(final UserHandle user) { return user.hashCode(); }

	public static boolean isSameApp(final int uid1, final int uid2) {
		return getAppId(uid1) == getAppId(uid2);
	}

	private static int getAppId(final int uid) {
		return uid % PER_USER_RANGE;
	}

	private static void ensureParentProfile() {
		if (mDebugBuild && ! isParentProfile()) throw new IllegalStateException("Not called in owner user");
	}

	private final BroadcastReceiver mProfileChangeObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final boolean added = Intent.ACTION_MANAGED_PROFILE_ADDED.equals(intent.getAction());
		final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
		Log.i(TAG, (added ? "Profile added: " : "Profile removed: ") + (user != null ? String.valueOf(toId(user)) : "null"));

		refreshUsers(context);
	}};

	private static boolean mDebugBuild;
	private static List<UserHandle> sProfilesManagedByIsland = null;	// Intentionally left null to fail early if this class is accidentally used in non-default process.
	private static boolean sCurrentProfileManagedByIsland = false;

	private static final int PER_USER_RANGE = 100000;
	private static final String TAG = "Island.Users";
}
