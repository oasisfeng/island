package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.Users;

import java.util.List;

import java8.util.Optional;
import java8.util.stream.Collectors;

/**
 * The procedure of managed profile shutdown or device owner deactivation.
 *
 * Created by Oasis on 2017/3/8.
 */
public class Shutdown {

	private static final int MAX_DESTROYING_APPS_LIST = 8;

	public static void requestDeviceOwnerDeactivation(final Activity activity) {
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning).setMessage(R.string.dialog_deactivate_message)
				.setPositiveButton(android.R.string.no, null)
				.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> {
					final List<String> frozen_pkgs = IslandAppListProvider.getInstance(activity).installedApps().filter(app -> app.isHidden())
							.map(app -> app.packageName).collect(Collectors.toList());
					if (! frozen_pkgs.isEmpty()) {
						if (IslandManager.useServiceInOwner(activity, island -> {
							try {
								for (final String pkg : frozen_pkgs) island.unfreezeApp(pkg);
							} finally {
								deactivateNow(activity);
							}
						})) return;		// Invoke deactivateNow() in the async procedure after all apps are unfrozen.
						Log.e(TAG, "Failed to connect to engine in owner user");
					}
					deactivateNow(activity);
				}).show();
	}

	private static void deactivateNow(final Activity activity) {
		new IslandManager(activity).deactivateDeviceOwner();
		activity.finishAffinity();	// Finish the whole activity stack.
		System.exit(0);		// Force termination of the whole app, to avoid potential inconsistency.
	}

	public static void requestProfileRemoval(final Activity activity) {
		final Optional<Boolean> is_profile_owner = IslandManager.isProfileOwner(activity);
		if (is_profile_owner == null || ! is_profile_owner.orElse(Boolean.FALSE)) {
			showPromptForProfileManualRemoval(activity);
			return;
		}
		final IslandAppListProvider provider = IslandAppListProvider.getInstance(activity);
		final List<String> exclusive_clones = provider.installedApps()
				.filter(app -> Users.isProfile(app.user) && ! app.isSystem() && provider.isExclusive(app))
				.map(AppInfo::getLabel).collect(Collectors.toList());
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
				.setMessage(R.string.dialog_destroy_message)
				.setPositiveButton(android.R.string.no, null)
				.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> {
					if (exclusive_clones.isEmpty()) {
						destroyProfile(activity);
						return;
					}
					final String names = Joiner.on('\n').skipNulls().join(Iterables.limit(exclusive_clones, MAX_DESTROYING_APPS_LIST));
					final String names_ellipsis = exclusive_clones.size() <= MAX_DESTROYING_APPS_LIST ? names : names + "…\n";
					new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
							.setMessage(activity.getString(R.string.dialog_destroy_exclusives_message, exclusive_clones.size(), names_ellipsis))
							.setNeutralButton(R.string.dialog_button_destroy, (dd, ww) -> destroyProfile(activity))
							.setPositiveButton(android.R.string.no, null).show();
				}).show();
	}

	private static void showPromptForProfileManualRemoval(final Activity activity) {
		final AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setMessage(R.string.dialog_cannot_destroy_message)
				.setNegativeButton(android.R.string.ok, null);
		final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
		if (intent.resolveActivity(activity.getPackageManager()) == null) intent.setAction(Settings.ACTION_SETTINGS);	// Fallback to entrance of Settings
		if (intent.resolveActivity(activity.getPackageManager()) != null)
			dialog.setPositiveButton(R.string.open_settings, (d, w) -> activity.startActivity(intent));
		dialog.show();
		Analytics.$().event("cannot_destroy").send();
	}

	private static void destroyProfile(final Activity activity) {
		if (! IslandManager.useServiceInProfile(activity, island -> {
			try {
				island.destroyProfile();
				ClonedHiddenSystemApps.reset(activity, Users.profile);
				activity.finishAffinity();	// Finish the whole activity stack.
				System.exit(0);		// Force terminate the whole app, to avoid potential inconsistency.
			} catch (final RemoteException ignored) {}
		})) showPromptForProfileManualRemoval(activity);
	}

	private static final String TAG = Shutdown.class.getSimpleName();
}
