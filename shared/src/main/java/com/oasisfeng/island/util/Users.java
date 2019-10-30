package com.oasisfeng.island.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;

import static android.content.Context.USER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for user-related helpers. Only works within the process where this provider is declared to be running.
 *
 * Created by Oasis on 2016/9/25.
 */
public abstract class Users extends PseudoContentProvider {

	public static @Nullable UserHandle profile;		// The first profile managed by Island (semi-immutable, until profile is created or destroyed)
	public static UserHandle owner;

	public static boolean hasProfile() { return profile != null; }

	private static final UserHandle CURRENT = Process.myUserHandle();
	private static final int CURRENT_ID = toId(CURRENT);

	public static UserHandle current() { return CURRENT; }

	@Override public boolean onCreate() {
		final int priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1;
		context().registerReceiver(mProfileChangeObserver,		// ACTION_MANAGED_PROFILE_ADDED is sent by DevicePolicyManagerService.setProfileEnabled()
				IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED).inPriority(priority));
		refreshUsers(context());
		return true;
	}

	/** This method should not be called under normal circumstance. */
	public static void refreshUsers(final Context context) {
		final List<UserHandle> owner_and_profiles = requireNonNull((UserManager) context.getSystemService(USER_SERVICE)).getUserProfiles();
		final List<UserHandle> profiles_managed_by_island = new ArrayList<>(owner_and_profiles.size() - 1);
		UserHandle first_profile_managed_by_island = null;
		for (final UserHandle user : owner_and_profiles) {
			if (isOwner(user)) owner = user;
			else if (DevicePolicies.isProfileOwner(context, user)) {
				profiles_managed_by_island.add(user);
				if (first_profile_managed_by_island == null) first_profile_managed_by_island = user;
				Log.i(TAG, "Profile managed by Island: " + toId(user));
			} else Log.i(TAG, "Profile not managed by Island: " + toId(user));
		}
		profile = first_profile_managed_by_island;
		sProfilesManagedByIsland = profiles_managed_by_island;
	}

	public static boolean isProfileRunning(final Context context, final UserHandle user) {
		if (CURRENT.equals(user)) return true;
		if (SDK_INT < N) return true;		// TODO: Alternative for pre-N ?
		final UserManager um = requireNonNull(context.getSystemService(UserManager.class));
		if (SDK_INT >= N_MR1) try {
			return um.isUserRunning(user);
		} catch (final RuntimeException e) {
			Log.w(TAG, "Error checking running state for user " + toId(user));
		}
		return um.isQuietModeEnabled(user);
	}

	public static boolean isOwner() { return CURRENT_ID == 0; }	// TODO: Support non-system primary user
	public static boolean isOwner(final UserHandle user) { return toId(user) == 0; }
	public static boolean isOwner(final int user_id) { return user_id == 0; }

	public static boolean isProfileManagedByIsland() { return isProfileManagedByIsland(CURRENT); }
	public static boolean isProfileManagedByIsland(final UserHandle user) {
		return user.equals(profile)/* fast path for first profile */ || sProfilesManagedByIsland.contains(user);
	}
	public static List<UserHandle> getProfilesManagedByIsland() { return Collections.unmodifiableList(sProfilesManagedByIsland); }

	public static int toId(final UserHandle user) { return user.hashCode(); }

	public static boolean isSameApp(final int uid1, final int uid2) {
		return getAppId(uid1) == getAppId(uid2);
	}

	private static int getAppId(final int uid) {
		return uid % PER_USER_RANGE;
	}

	private final BroadcastReceiver mProfileChangeObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent i) {
		Log.i(TAG, "Profile changed");
		refreshUsers(context);
	}};

	private static final int PER_USER_RANGE = 100000;
	private static List<UserHandle> sProfilesManagedByIsland = null;	// Intentionally left null to fail early if this class is accidentally used in non-default process.
	private static final String TAG = "Island.Users";
}
