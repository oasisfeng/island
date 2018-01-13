package com.oasisfeng.island.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.List;

import static android.content.Context.USER_SERVICE;

/**
 * Utility class for user-related helpers.
 *
 * Created by Oasis on 2016/9/25.
 */
public abstract class Users extends PseudoContentProvider {

	public static @Nullable UserHandle profile;		// Semi-immutable (until profile is created or destroyed)
	public static UserHandle owner;

	public static boolean hasProfile() { return profile != null; }

	private static final UserHandle CURRENT = android.os.Process.myUserHandle();
	private static final int CURRENT_ID = toId(CURRENT);

	public static UserHandle current() { return CURRENT; }

	@Override public boolean onCreate() {
		final int priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1;
		context().registerReceiver(mProfileChangeObserver,
				IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED).inPriority(priority));
		refreshUsers(context());
		return true;
	}

	/** This method should not be called under normal circumstance. */
	public static void refreshUsers(final Context context) {
		profile = null;
		final UserManager um = ((UserManager) context.getSystemService(USER_SERVICE));
		if (um == null) return;
		final List<UserHandle> user_and_profiles = um.getUserProfiles();
		for (final UserHandle user_or_profile : user_and_profiles) {
			if (! isOwner(user_or_profile)) {
				if (profile == null) profile = user_or_profile;        // Only one managed profile is supported by Android framework at present.
			} else owner = user_or_profile;
		}
		sProfileId = profile != null ? toId(profile) : -1;
		Log.i(TAG, "Profile ID: " + sProfileId);
	}

	public static boolean isOwner() { return CURRENT_ID == 0; }	// TODO: Support non-system primary user
	public static boolean isOwner(final UserHandle user) { return toId(user) == 0; }

	public static boolean isProfile() { return CURRENT_ID == sProfileId; }
	public static boolean isProfile(final UserHandle user) { return toId(user) == sProfileId; }

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
	private static int sProfileId = -1;		// -1 if no profile
	private static final String TAG = "Users";
}
