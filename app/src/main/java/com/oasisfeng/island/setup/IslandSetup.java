package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.DeadObjectException;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.Users;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;
import eu.chainfire.libsuperuser.Shell;
import java9.util.stream.Collectors;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static com.oasisfeng.island.analytics.Analytics.Param.CONTENT;
import static java9.util.stream.Collectors.joining;
import static java9.util.stream.StreamSupport.stream;

/**
 * Implementation of Island / Mainland setup & shutdown.
 *
 * Created by Oasis on 2017/3/8.
 */
public class IslandSetup {

	private static final int MAX_DESTROYING_APPS_LIST = 8;

	static final String RES_MAX_USERS = "config_multiuserMaximumUsers";
	private static final String PACKAGE_VERIFIER_INCLUDE_ADB = "verifier_verify_adb_installs";


	public static void requestProfileOwnerSetupWithRoot(final Activity activity) {
		final ProgressDialog progress = ProgressDialog.show(activity, null, "Setup Island...", true);
		// Phase 1: Create profile		TODO: Skip profile creation or remove existent profile first, if profile is already present (probably left by unsuccessful setup)
		final List<String> commands = Arrays.asList("setprop fw.max_users 10",
				"pm create-user --profileOf " + Users.toId(Process.myUserHandle()) + " --managed Island", "echo END");
		SafeAsyncTask.execute(activity, a -> Shell.SU.run(commands), result -> {
			Users.refreshUsers(activity);
			if (! Users.hasProfile()) {		// Profile creation failed
				if (result == null || result.isEmpty()) return;		// Just root failure
				Analytics.$().event("setup_island_root_failed").withRaw("commands", stream(commands).collect(joining("\n")))
						.withRaw("fw_max_users", String.valueOf(getSysPropMaxUsers()))
						.withRaw("config_multiuserMaximumUsers", String.valueOf(getResConfigMaxUsers()))
						.with(CONTENT, stream(result).collect(joining("\n"))).send();
				dismissProgressAndShowError(activity, progress, 1);
				return;
			}

			installIslandInProfileWithRoot(activity, progress);
		});
	}

	// Phase 2: Install Island app inside
	private static void installIslandInProfileWithRoot(final Activity activity, final ProgressDialog progress) {
		// Disable package verifier before installation, to avoid hanging too long.
		final StringBuilder commands = new StringBuilder();
		final String adb_verify_value_before = Settings.Global.getString(activity.getContentResolver(), PACKAGE_VERIFIER_INCLUDE_ADB);
		if (adb_verify_value_before == null || Integer.parseInt(adb_verify_value_before) != 0)
			commands.append("settings put global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(" 0 ; ");

		final ApplicationInfo info; try {
			info = activity.getPackageManager().getApplicationInfo(Modules.MODULE_ENGINE, 0);
		} catch (final NameNotFoundException e) { return; }	// Should never happen.
		final int profile_id = Users.toId(Users.profile);
		commands.append("pm install -r --user ").append(profile_id).append(' ');
		if (BuildConfig.DEBUG) commands.append("-t ");
		commands.append(info.sourceDir).append(" && ");

		if (adb_verify_value_before == null) commands.append("settings delete global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(" ; ");
		else commands.append("settings put global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(' ').append(adb_verify_value_before).append(" ; ");

		// All following commands must be executed all together with the above one, since this app process will be killed upon "pm install".
		final String flat_admin_component = DeviceAdmins.getComponentName(activity).flattenToString();
		commands.append(SDK_INT >= M ? "dpm set-profile-owner --user " + profile_id + " " + flat_admin_component
				: "dpm set-profile-owner " + flat_admin_component + " " + profile_id);
		commands.append(" && am start-user ").append(profile_id);

		SafeAsyncTask.execute(activity, a -> Shell.SU.run(commands.toString()), result -> {
			final LauncherApps launcher_apps = Objects.requireNonNull((LauncherApps) activity.getSystemService(Context.LAUNCHER_APPS_SERVICE));
			if (launcher_apps.getActivityList(activity.getPackageName(), Users.profile).isEmpty()) {
				Analytics.$().event("setup_island_root_failed").withRaw("command", commands.toString())
						.with(CONTENT, result == null ? "<null>" : stream(result).collect(joining("\n"))).send();
				dismissProgressAndShowError(activity, progress, 2);
			}
		});
	}

	private static void dismissProgressAndShowError(final Activity activity, final ProgressDialog progress, final int stage) {
		progress.dismiss();
		Dialogs.buildAlert(activity, null, activity.getString(R.string.dialog_island_setup_failed, stage)).withOkButton(null).show();
	}

	static @Nullable Integer getSysPropMaxUsers() {
		return Hacks.SystemProperties_getInt.invoke("fw.max_users", - 1).statically();
	}

	static @Nullable Integer getResConfigMaxUsers() {
		final Resources sys_res = Resources.getSystem();
		final int res = sys_res.getIdentifier(RES_MAX_USERS, "integer", "android");
		if (res == 0) return null;
		return Resources.getSystem().getInteger(res);
	}

	public static void requestDeviceOwnerActivation(final Fragment fragment, final int request_code) {
		Dialogs.buildAlert(fragment.getActivity(), fragment.getText(R.string.featured_god_mode_title),
				fragment.getText(R.string.featured_god_mode_description) + "\n\n" + fragment.getText(R.string.dialog_activate_god_mode_additional_text))
				.setPositiveButton(R.string.dialog_button_continue, (d, w) -> activateDeviceOwnerOrShowSetupGuide(fragment, request_code)).show();
	}

	private static void activateDeviceOwnerOrShowSetupGuide(final Fragment fragment, final int request_code) {
		final Activity activity = fragment.getActivity();
		if (activity == null) return;
		String content = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><device-owner package=\"" + Modules.MODULE_ENGINE + "\" />";
		final String admin_component = DeviceAdmins.getComponentName(activity).flattenToString();
		if (Users.profile != null && DevicePolicies.isProfileOwner(activity, Users.profile))
			content += "<profile-owner package=\"" + Modules.MODULE_ENGINE + "\" name=\"Island\" userId=\"" + Users.toId(Users.profile)
					+ "\" component=\"" + admin_component + "\" />";
		content = content.replace("\"", "\\\"").replace("'", "\\'")
				.replace("<", "\\<").replace(">", "\\>");

		final String file = new File(getDataSystemDirectory(), "device_owner.xml").getAbsolutePath();
		final StringBuilder command = new StringBuilder("echo ").append(content).append(" > ").append(file)
				.append(" && chmod 600 ").append(file).append(" && chown system:system ").append(file);
		if (SDK_INT >= LOLLIPOP_MR1) command.append(" && dpm set-active-admin ").append(admin_component).append(" ; echo DONE");
		else command.append(" && echo DONE");

		SafeAsyncTask.execute(activity, a -> Shell.SU.run(command.toString()), output -> {
			if (activity.isDestroyed() || activity.isFinishing()) return;
			if (output == null || output.isEmpty()) {
				Toast.makeText(activity, R.string.toast_setup_mainland_non_root, Toast.LENGTH_LONG).show();
				WebContent.view(activity, Uri.parse(Config.URL_SETUP.get()));
				return;
			}
			if (! "DONE".equals(output.get(output.size() - 1))) {
				Analytics.$().event("setup_mainland_root").with(CONTENT, stream(output).collect(joining("\n"))).send();
				Toast.makeText(activity, R.string.toast_setup_mainland_root_failed, Toast.LENGTH_LONG).show();
				return;
			}
			Analytics.$().event("setup_mainland_root").with(CONTENT, output.size() == 1/* DONE */? null : stream(output).collect(joining("\n"))).send();
			// Start the device-admin activation UI (no-op if already activated with root above), since "dpm set-active-admin" is not supported on Android 5.0.
			fragment.startActivityForResult(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
					.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(activity))
					.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.dialog_mainland_device_admin)), request_code);
			// Procedure is followed in onAddAdminResult().
		});
	}

	private static File getDataSystemDirectory() {
		if (Hacks.Environment_getDataSystemDirectory != null) return Hacks.Environment_getDataSystemDirectory.invoke().statically();
		final String data_path = System.getenv("ANDROID_DATA");
		return new File(data_path == null ? new File("/data") : new File(data_path), "system");
	}

	public static void onAddAdminResult(final Activity activity) {
		if (! new DevicePolicies(activity).invoke(DevicePolicyManager::isAdminActive)) return;
		Dialogs.buildAlert(activity, 0, R.string.dialog_mainland_setup_done).withCancelButton()
				.setPositiveButton(R.string.dialog_button_reboot, (d, w) -> SafeAsyncTask.execute(() -> Shell.SU.run("reboot"))).show();
	}

	public static void requestDeviceOwnerDeactivation(final Activity activity) {
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning).setMessage(R.string.dialog_deactivate_message)
				.setPositiveButton(android.R.string.no, null)
				.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> {
					try {
						final List<String> frozen_pkgs = IslandAppListProvider.getInstance(activity).installedApps().filter(app -> app.isHidden())
								.map(app -> app.packageName).collect(Collectors.toList());
						if (! frozen_pkgs.isEmpty()) {
							final DevicePolicies policies = new DevicePolicies(activity);
							for (final String pkg : frozen_pkgs)
								policies.setApplicationHidden(pkg, false);
						}
					} finally {
						deactivateDeviceOwner(activity);
					}
				}).show();
	}

	private static void deactivateDeviceOwner(final Activity activity) {
		Analytics.$().event("action_deactivate").send();
		final DevicePolicies policies = new DevicePolicies(activity);
		policies.getManager().clearDeviceOwnerApp(activity.getPackageName());
		try {	// Since Android 7.1, clearDeviceOwnerApp() itself does remove active device-admin,
			policies.execute(DevicePolicyManager::removeActiveAdmin);
		} catch (final SecurityException ignored) {}		//   thus SecurityException will be thrown here.

		activity.finishAffinity();	// Finish the whole activity stack.
		System.exit(0);		// Force termination of the whole app, to avoid potential inconsistency.
	}

	public static void requestProfileRemoval(final Activity activity) {
		if (Users.profile == null || ! DevicePolicies.isProfileOwner(activity, Users.profile)) {
			showPromptForProfileManualRemoval(activity);
			return;
		}
		final IslandAppListProvider provider = IslandAppListProvider.getInstance(activity);
		final List<String> exclusive_clones = provider.installedApps()
				.filter(app -> Users.isProfileManagedByIsland(app.user) && ! app.isSystem() && provider.isExclusive(app))
				.map(AppInfo::getLabel).collect(Collectors.toList());
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
				.setMessage(R.string.dialog_destroy_message)
				.setPositiveButton(android.R.string.no, null)
				.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> {
					if (exclusive_clones.isEmpty()) {
						destroyProfile(activity);
						return;
					}
					final String names = stream(exclusive_clones).limit(MAX_DESTROYING_APPS_LIST).collect(joining("\n"));
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
		MethodShuttle.runInProfile(activity, () -> {
			final DevicePolicies policies = new DevicePolicies(context);
			policies.execute(DevicePolicyManager::clearCrossProfileIntentFilters);
			policies.getManager().wipeData(0);
		}).whenComplete((result, e) -> {
			if (e != null && ! (e instanceof DeadObjectException)) {	// DeadObjectException is normal, as wipeData() also terminated the calling process.
				showPromptForProfileManualRemoval(activity);
				return;
			}
			ClonedHiddenSystemApps.reset(activity, Users.profile);
			activity.finishAffinity();	// Finish the whole activity stack.
			System.exit(0);		// Force terminate the whole app, to avoid potential inconsistency.
		});
	}

	private static final String TAG = IslandSetup.class.getSimpleName();
}
