package com.oasisfeng.island.setup;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.util.SafeAsyncTask;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.hack.Hack;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Hacks.UserManagerHack;
import com.oasisfeng.island.util.Hacks.UserManagerHack.UserInfo;
import com.oasisfeng.island.util.Modules;
import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import eu.chainfire.libsuperuser.Shell;

import static com.oasisfeng.island.analytics.Analytics.Param.CONTENT;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Implementation of Island / Mainland setup & shutdown.
 *
 * Created by Oasis on 2017/3/8.
 */
public class IslandSetup {

	static final String RES_MAX_USERS = "config_multiuserMaximumUsers";
	private static final String PACKAGE_VERIFIER_INCLUDE_ADB = "verifier_verify_adb_installs";

	public static void requestProfileOwnerSetupWithRoot(final Activity activity) {
		final ProgressDialog progress = ProgressDialog.show(activity, null, "Setup Island...", true);
		// Phase 1: Create profile		TODO: Skip profile creation or remove existent profile first, if profile is already present (probably left by unsuccessful setup)
		final List<String> commands = Arrays.asList("setprop fw.max_users 10",
				"pm create-user --profileOf " + Users.toId(Process.myUserHandle()) + " --managed Island", "echo END");
		SafeAsyncTask.execute(activity, context -> Shell.SU.run(commands), (context, result) -> {
			final List<UserInfo> profiles = Hack.into(requireNonNull(context.getSystemService(Context.USER_SERVICE)))
					.with(UserManagerHack.class).getProfiles(Users.toId(Users.current()));
			final Optional<UserHandle> profile_pending_setup = profiles.stream().map(UserInfo::getUserHandle)
					.filter(profile -> ! profile.equals(Users.current()) && isProfileWithoutOwner(context, profile)).findFirst();	// Not yet set up as profile owner
			if (! profile_pending_setup.isPresent()) {		// Profile creation failed
				if (result == null || result.isEmpty()) return;		// Just root failure
				Analytics.$().event("setup_island_root_failed").withRaw("commands", commands.stream().collect(joining("\n")))
						.withRaw("fw_max_users", String.valueOf(getSysPropMaxUsers()))
						.withRaw("config_multiuserMaximumUsers", String.valueOf(getResConfigMaxUsers()))
						.with(CONTENT, result.stream().collect(joining("\n"))).send();
				dismissProgressAndShowError(context, progress, 1);
				return;
			}

			installIslandInProfileWithRoot(context, progress, profile_pending_setup.get());
		});
	}

	private static boolean isProfileWithoutOwner(final Context context, final UserHandle profile) {
		final Optional<ComponentName> owner = DevicePolicies.getProfileOwnerAsUser(context, profile);
		return owner == null || ! owner.isPresent();
	}

	// Phase 2: Install Island app inside
	private static void installIslandInProfileWithRoot(final Activity activity, final ProgressDialog progress, final UserHandle profile) {
		// Disable package verifier before installation, to avoid hanging too long.
		final StringBuilder commands = new StringBuilder();
		final String adb_verify_value_before = Settings.Global.getString(activity.getContentResolver(), PACKAGE_VERIFIER_INCLUDE_ADB);
		if (adb_verify_value_before == null || Integer.parseInt(adb_verify_value_before) != 0)
			commands.append("settings put global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(" 0 ; ");

		final ApplicationInfo info; try {
			info = activity.getPackageManager().getApplicationInfo(Modules.MODULE_ENGINE, 0);
		} catch (final NameNotFoundException e) { return; }	// Should never happen.
		final int profile_id = Users.toId(profile);
		commands.append("pm install -r --user ").append(profile_id).append(' ');
		if (BuildConfig.DEBUG) commands.append("-t ");
		commands.append(info.sourceDir).append(" && ");

		if (adb_verify_value_before == null) commands.append("settings delete global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(" ; ");
		else commands.append("settings put global ").append(PACKAGE_VERIFIER_INCLUDE_ADB).append(' ').append(adb_verify_value_before).append(" ; ");

		// All following commands must be executed all together with the above one, since this app process will be killed upon "pm install".
		final String flat_admin_component = DeviceAdmins.getComponentName(activity).flattenToString();
		commands.append("dpm set-profile-owner --user ").append(profile_id).append(" ").append(flat_admin_component);
		commands.append(" && am start-user ").append(profile_id);

		SafeAsyncTask.execute(activity, context -> Shell.SU.run(commands.toString()), (context, result) -> {
			final LauncherApps launcher_apps = requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE));
			if (launcher_apps.getActivityList(context.getPackageName(), profile).isEmpty()) {
				Analytics.$().event("setup_island_root_failed").withRaw("command", commands.toString())
						.with(CONTENT, result == null ? "<null>" : result.stream().collect(joining("\n"))).send();
				dismissProgressAndShowError(context, progress, 2);
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

	@OwnerUser public static void requestDeviceOrProfileOwnerDeactivation(final Activity activity) {
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning).setMessage(R.string.dialog_rescind_message)
				.setPositiveButton(android.R.string.no, null).setNeutralButton(R.string.action_rescind, (d, w) -> {
					try {
						final DevicePolicies policies = new DevicePolicies(activity);
						final AppListProvider<AppInfo> provider = AppListProvider.getInstance(activity);
						final Stream<AppInfo> apps = provider.installedAppsInOwnerUser();

						final List<String> frozen_pkgs = apps.filter(AppInfo::isHidden).map(app -> app.packageName).collect(toList());
						for (final String pkg : frozen_pkgs)
							policies.setApplicationHidden(pkg, false);

						final String[] suspended_pkgs = apps.filter(AppInfo::isSuspended).map(app -> app.packageName).toArray(String[]::new);
						policies.invoke(DevicePolicyManager::setPackagesSuspended, suspended_pkgs, false);
					} finally {
						deactivateDeviceOrProfileOwner(activity);
					}
				}).show();
	}

	private static void deactivateDeviceOrProfileOwner(final Activity activity) {
		Analytics.$().event("action_deactivate").send();
		final DevicePolicies policies = new DevicePolicies(activity);
		if (policies.isActiveDeviceOwner())
			policies.getManager().clearDeviceOwnerApp(activity.getPackageName());
		else clearProfileOwner(policies);
		try {	// Since Android 7.1, clearDeviceOwnerApp() itself does remove active device-admin,
			policies.execute(DevicePolicyManager::removeActiveAdmin);
		} catch (final SecurityException ignored) {}		//   thus SecurityException will be thrown here.

		activity.finishAffinity();	// Finish the whole activity stack.
		System.exit(0);		// Force termination of the whole app, to avoid potential inconsistency.
	}

	@SuppressLint("NewApi"/* hidden before N */) private static void clearProfileOwner(final DevicePolicies policies) {
		policies.execute(DevicePolicyManager::clearProfileOwner);
	}

	@ProfileUser public static void requestProfileRemoval(final Activity activity) {
		if (Users.isOwner()) throw new IllegalStateException("Must be called in managed profile");
		if (! DevicePolicies.isProfileOwner(activity, Users.current())) {
			showPromptForProfileManualRemoval(activity);
			return;
		}
		new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning).setNeutralButton(R.string.action_destroy, (dd, ww) -> {
			if (! DevicePolicies.isProfileOwner(activity, Users.owner)) destroyProfile(activity);
			else new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning).setMessage(R.string.dialog_destroy_message_for_managed_user)
					.setNeutralButton(R.string.action_destroy, (d, w) -> destroyProfile(activity))
					.setPositiveButton(android.R.string.no, null).show();
		}).setMessage(R.string.dialog_destroy_message).setPositiveButton(android.R.string.no, null).show();
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

	@ProfileUser private static void destroyProfile(final Activity activity) {
		if (Users.isOwner()) throw new IllegalStateException("Must be called in managed profile.");
		final DevicePolicies policies = new DevicePolicies(activity);
		try {
			policies.execute(DevicePolicyManager::clearCrossProfileIntentFilters);
			policies.getManager().wipeData(0);
		} catch(final RuntimeException e) {
			showPromptForProfileManualRemoval(activity);
		}
	}
}
