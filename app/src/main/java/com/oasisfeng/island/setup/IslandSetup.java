package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.DeadObjectException;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import eu.chainfire.libsuperuser.Shell;
import java9.util.Optional;
import java9.util.stream.Collectors;

import static com.oasisfeng.island.analytics.Analytics.Param.CONTENT;

/**
 * Implementation of Island / Mainland setup & shutdown.
 *
 * Created by Oasis on 2017/3/8.
 */
public class IslandSetup {

	private static final int MAX_DESTROYING_APPS_LIST = 8;

	public static void requestDeviceOwnerActivation(final Fragment fragment, final int request_code) {
		Dialogs.buildAlert(fragment.getActivity(), R.string.pref_setup_mainland_activate_title, R.string.pref_setup_mainland_activate_text)
				.setPositiveButton(R.string.dialog_button_continue, (d, w) -> activateDeviceOwnerOrShowSetupGuide(fragment, request_code)).show();
	}

	private static void activateDeviceOwnerOrShowSetupGuide(final Fragment fragment, final int request_code) {
		final Activity activity = fragment.getActivity();
		if (activity == null) return;
		String content = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><device-owner package=\"" + Modules.MODULE_ENGINE + "\" />";
		final Optional<Boolean> is_profile_owner;
		if (Users.profile != null && (is_profile_owner = DevicePolicies.isProfileOwner(activity, Users.profile)) != null && is_profile_owner.orElse(false))
			content += "<profile-owner package=\"" + Modules.MODULE_ENGINE + "\" name=\"Island\" userId=\"" + Users.toId(Users.profile)
					+ "\" component=\"" + DeviceAdmins.getComponentName(activity).flattenToString() + "\" />";
		content = content.replace("\"", "\\\"").replace("'", "\\'")
				.replace("<", "\\<").replace(">", "\\>");

		final String file = new File(Hacks.Environment_getSystemSecureDirectory.invoke().statically(), "device_owner.xml").getAbsolutePath();
		final String command = "echo " + content + " > " + file + " && chmod 600 " + file + " && chown system:system " + file + " && echo DONE";

		SafeAsyncTask.execute(activity, a -> Shell.SU.run(command), output -> {
			if (activity.isDestroyed() || activity.isFinishing()) return;
			if (output == null || output.isEmpty()) {
				Toast.makeText(activity, R.string.toast_setup_god_mode_non_root, Toast.LENGTH_LONG).show();
				WebContent.view(activity, Uri.parse(Config.URL_SETUP.get()));
				return;
			}
			if (! "DONE".equals(output.get(output.size() - 1))) {
				Analytics.$().event("error_activating_device_owner_root").with(CONTENT, Joiner.on('\n').join(output)).send();
				Toast.makeText(activity, R.string.toast_setup_god_mode_root_failed, Toast.LENGTH_LONG).show();
				return;
			}
			Analytics.$().event("activate_device_owner_root").with(CONTENT, output.size() == 1/* DONE */? null : Joiner.on('\n').join(output)).send();
			fragment.startActivityForResult(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
					.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(activity))
					.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.dialog_mainland_device_admin)), request_code);
			// Procedure is followed in onAddAdminResult().
		});
	}

	public static void onAddAdminResult(final Activity activity) {
		if (! new DevicePolicies(activity).isAdminActive()) return;
		Dialogs.buildAlert(activity, 0, R.string.dialog_mainland_setup_done).withCancelButton()
				.setPositiveButton(R.string.dialog_button_reboot, (d, w) -> SafeAsyncTask.execute(() -> Shell.SU.run("reboot"))).show();
	}

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
								deactivateDeviceOwner(activity);
							}
						})) return;		// Invoke deactivateNow() in the async procedure after all apps are unfrozen.
						Log.e(TAG, "Failed to connect to engine in owner user");
					}
					deactivateDeviceOwner(activity);
				}).show();
	}

	private static void deactivateDeviceOwner(final Activity activity) {
		new IslandManager(activity).deactivateDeviceOwner();
		activity.finishAffinity();	// Finish the whole activity stack.
		System.exit(0);		// Force termination of the whole app, to avoid potential inconsistency.
	}

	public static void requestProfileRemoval(final Activity activity) {
		final Optional<Boolean> is_profile_owner = DevicePolicies.isOwnerOfEnabledProfile(activity);
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
		@SuppressWarnings("UnnecessaryLocalVariable") final Context context = activity;		// MethodShuttle accepts only Context, but not Activity.
		final ListenableFuture<Void> future = MethodShuttle.runInProfile(activity, () -> {
			final DevicePolicies policies = new DevicePolicies(context);
			policies.clearCrossProfileIntentFilters();
			policies.getManager().wipeData(0);
		});
		future.addListener(() -> {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				if (! (e instanceof ExecutionException && e.getCause() instanceof DeadObjectException))	// DeadObjectException is normal, as wipeData() also terminated the calling process.
					showPromptForProfileManualRemoval(activity);
				return;
			}
			ClonedHiddenSystemApps.reset(activity, Users.profile);
			activity.finishAffinity();	// Finish the whole activity stack.
			System.exit(0);		// Force terminate the whole app, to avoid potential inconsistency.
		}, MoreExecutors.directExecutor());
	}

	private static final String TAG = IslandSetup.class.getSimpleName();
}
