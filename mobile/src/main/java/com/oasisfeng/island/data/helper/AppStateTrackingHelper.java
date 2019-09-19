package com.oasisfeng.island.data.helper;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.UserHandle;

import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.util.Users;

/**
 * Helper for tracking the state of apps.
 *
 * Created by Oasis on 2019-1-23.
 */
public class AppStateTrackingHelper {

	/** Force refresh the state of specified app when activity is resumed */
	public static void requestSyncWhenResumed(final Activity activity, final String pkg, final UserHandle user) {
		activity.getFragmentManager().beginTransaction().add(AppStateSyncFragment.create(pkg, user), pkg + "@" + Users.toId(user)).commit();
	}

	public static class AppStateSyncFragment extends Fragment {

		private static final String KEY_PACKAGE = "package", KEY_USER = "user";

		@Override public void onResume() {
			super.onResume();
			final Bundle args = getArguments();
			final Activity activity = getActivity();
			IslandAppListProvider.getInstance(activity).refreshPackage(args.getString(KEY_PACKAGE), args.getParcelable(KEY_USER), false);
			activity.getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
		}

		private static AppStateSyncFragment create(final String pkg, final UserHandle user) {
			final AppStateSyncFragment fragment = new AppStateSyncFragment();
			final Bundle args = new Bundle();
			args.putString(KEY_PACKAGE, pkg);
			args.putParcelable(KEY_USER, user);
			fragment.setArguments(args);
			return fragment;
		}
	}
}
