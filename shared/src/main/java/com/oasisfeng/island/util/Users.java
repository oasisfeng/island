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
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.pattern.LocalContentProvider;

import java.util.List;

import static android.content.Context.USER_SERVICE;

/**
 * Utility class for user-related helpers.
 *
 * Created by Oasis on 2016/9/25.
 */
public class Users extends LocalContentProvider {

	private static final UserHandle CURRENT = android.os.Process.myUserHandle();
	private static final int CURRENT_ID = toId(CURRENT);

	public static UserHandle current() { return CURRENT; }

	@Override public boolean onCreate() {
		final int priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1;
		context().registerReceiver(mProfileChangeObserver,
				IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED).inPriority(priority));
		refreshUsers();
		return true;
	}

	private void refreshUsers() {
		final UserHandle profile = queryProfile();
		sProfileId = profile != null ? toId(profile) : 0;
		Log.i(TAG, "Profile ID: " + profile);
		GlobalStatus.profile = profile;
	}

	private @Nullable UserHandle queryProfile() {
		final UserManager um = (UserManager) context().getSystemService(USER_SERVICE);
		final List<UserHandle> user_and_profiles = um.getUserProfiles();
		for (final UserHandle user_or_profile : user_and_profiles)
			if (! isOwner(user_or_profile))
				return user_or_profile;		// Only one managed profile is supported by Android at present.
		return null;
	}

	public static boolean isOwner() { return CURRENT_ID == 0; }	// TODO: Support non-system primary user
	public static boolean isOwner(final UserHandle user) { return toId(user) == 0; }

	public static boolean isProfile() { return CURRENT_ID != 0 && sProfileId == CURRENT_ID; }
	public static boolean isProfile(final UserHandle user) { return toId(user) == sProfileId; }

	public static int toId(final UserHandle user) { return user.hashCode(); }

	private final BroadcastReceiver mProfileChangeObserver = new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent i) {
		Log.i(TAG, "Profile changed");
		refreshUsers();
	}};

	private static int sProfileId;		// 0 if no profile
	private static final String TAG = "Users";
}
