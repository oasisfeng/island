package com.oasisfeng.island.adb;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

import com.oasisfeng.android.app.LifecycleActivity;
import com.oasisfeng.android.ui.Snackbars;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.LiveUserRestriction;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.security.SecurityPrompt;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by Oasis on 2019-5-24.
 */
public class AdbSecure {

	private static final String PREF_KEY_ADB_SECURE_PROTECTED = "adb_secure_protected";

	public static void toggleAdbSecure(final LifecycleActivity activity, final boolean enabling, final boolean security_confirmed) {
		if (! enabling && ! security_confirmed && SDK_INT >= M && isAdbSecureProtected(activity)) {
			requestSecurityConfirmationBeforeDisablingAdbSecure(activity);
			return;
		}

		final DevicePolicies policies = new DevicePolicies(activity);
		if (policies.isActiveDeviceOwner()) {
			if (! enabling) {
				policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
				policies.execute(DevicePolicyManager::setGlobalSetting, Settings.Global.ADB_ENABLED, "1");	// DISALLOW_DEBUGGING_FEATURES also disables ADB.
			} else policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
		}
		if (! Users.hasProfile()) {
			showPromptForAdbSecureProtection(activity, enabling);
			return;		// No managed profile, all done.
		}

		final Context app_context = activity.getApplication();
		if (SDK_INT < N || ! activity.getSystemService(UserManager.class).isQuietModeEnabled(Users.profile))
			MethodShuttle.runInProfile(app_context, () -> {
				final DevicePolicies device_policies = new DevicePolicies(app_context);	// The "policies" instance can not be passed into profile.
				if (enabling) device_policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
				else device_policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
				return enabling;
			}).whenComplete((enabled, e) -> {
				if (e != null) {
					Analytics.$().logAndReport(TAG, "Error setting featured button", e);
					Toast.makeText(app_context, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
				} else {
					LiveUserRestriction.notifyUpdate(app_context);	// Request explicit update, since observer does not work across users.
					showPromptForAdbSecureProtection(activity, enabled);
				}
			});
	}

	private static void showPromptForAdbSecureProtection(final LifecycleActivity activity, final boolean enabled) {
		if (SDK_INT < M || ! enabled || activity.isDestroyed()) return;
		if (isAdbSecureProtected(activity)) Snackbars.make(activity, R.string.prompt_security_confirmation_activated)
				.setAction(R.string.action_deactivate, v -> disableSecurityConfirmationForAdbSecure(activity)).show();
		else Snackbars.make(activity, R.string.prompt_security_confirmation_suggestion)
				.setAction(R.string.action_activate, v -> enableSecurityConfirmationForAdbSecure(activity)).show();
	}

	private static boolean isAdbSecureProtected(final Context context) {
		return getDefaultSharedPreferences(context).getBoolean(PREF_KEY_ADB_SECURE_PROTECTED, false);
	}

	@RequiresApi(M) private static void enableSecurityConfirmationForAdbSecure(final LifecycleActivity activity) {
		if (activity.isDestroyed()) return;
		final Application app = activity.getApplication();
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_activating, () -> {
			getDefaultSharedPreferences(app).edit().putBoolean(PREF_KEY_ADB_SECURE_PROTECTED, true).apply();
			Toast.makeText(app, R.string.toast_security_confirmation_activated, Toast.LENGTH_SHORT).show();
		});
	}

	@RequiresApi(M) private static void disableSecurityConfirmationForAdbSecure(final LifecycleActivity activity) {
		if (activity.isDestroyed()) return;
		final Application app = activity.getApplication();
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_deactivating, () -> {
			getDefaultSharedPreferences(app).edit().putBoolean(PREF_KEY_ADB_SECURE_PROTECTED, false).apply();
			Toast.makeText(app, R.string.toast_security_confirmation_deactivated, Toast.LENGTH_SHORT).show();
		});
	}

	@RequiresApi(M) private static void requestSecurityConfirmationBeforeDisablingAdbSecure(final LifecycleActivity activity) {
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_to_disable,
				() -> toggleAdbSecure(activity, false, true));
	}

	private static final String TAG = "Island.ADBS";
}
