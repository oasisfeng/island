package com.oasisfeng.island.controller;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.installer.InstallerExtras;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.island.util.Users;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import eu.chainfire.libsuperuser.Shell;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/**
 * Controller for complex procedures of Island.
 *
 * Refactored by Oasis on 2018-9-30.
 */
public class IslandAppClones {

	private static final String SCHEME_PACKAGE = "package";

	private static final int CLONE_RESULT_ALREADY_CLONED = 0;
	private static final int CLONE_RESULT_OK_INSTALL = 1;
	private static final int CLONE_RESULT_OK_INSTALL_EXISTING = 2;
	private static final int CLONE_RESULT_OK_GOOGLE_PLAY = 10;
	private static final int CLONE_RESULT_UNKNOWN_SYS_MARKET = 11;
	private static final int CLONE_RESULT_NOT_FOUND = -1;
	private static final int CLONE_RESULT_NO_SYS_MARKET = -2;

	public static void cloneApp(final Context context, final IslandAppInfo source) {
		final String pkg = source.packageName;
		if (source.isSystem()) {
			Analytics.$().event("clone_sys").with(ITEM_ID, pkg).send();
			MethodShuttle.runInProfile(context, () -> new DevicePolicies(context).enableSystemApp(pkg)).thenAccept(enabled ->
				Toast.makeText(context, context.getString(enabled ? R.string.toast_successfully_cloned : R.string.toast_cannot_clone, source.getLabel()),
						enabled ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show()
			).exceptionally(throwable -> {
				reportAndShowToastForInternalException(context, "Error cloning system app: " + pkg, throwable);
				return null;
			});
			return;
		}
		@SuppressWarnings("UnnecessaryLocalVariable") final ApplicationInfo info = source;	// cloneUserApp() accepts ApplicationInfo, not IslandAppInfo.
		MethodShuttle.runInProfile(context, () -> new IslandAppClones(context).performUserAppCloning(info.packageName, info, false)).thenAccept(result -> {
			switch (result) {
			case CLONE_RESULT_ALREADY_CLONED:
				Toast.makeText(context, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show();
				break;
			case CLONE_RESULT_NO_SYS_MARKET:
				Activity activity = Activities.findActivityFrom(context);
				if (activity != null) Dialogs.buildAlert(activity, 0, R.string.dialog_clone_incapable_explanation)
						.setNeutralButton(R.string.dialog_button_learn_more, (d, w) -> WebContent.view(context, Config.URL_FAQ.get()))
						.setPositiveButton(android.R.string.cancel, null).show();
				else Toast.makeText(context, R.string.dialog_clone_incapable_explanation, Toast.LENGTH_LONG).show();
				break;
			case CLONE_RESULT_OK_INSTALL:
				Analytics.$().event("clone_install").with(ITEM_ID, pkg).send();
				final UserHandle profile = Objects.requireNonNull(Users.profile);
				if (SDK_INT >= O) cloneAppViaRootWithFallback(context, source, profile, IslandAppClones::cloneAppViaInstaller);
				else cloneAppViaInstaller(context, source);
				break;
			case CLONE_RESULT_OK_INSTALL_EXISTING:
				Analytics.$().event("clone_install_existing").with(ITEM_ID, pkg).send();
				doCloneUserApp(context, source);		// No explanation needed.
				break;
			case CLONE_RESULT_OK_GOOGLE_PLAY:
				Analytics.$().event("clone_via_play").with(ITEM_ID, pkg).send();
				showExplanationBeforeCloning("clone-via-google-play-explained", context, R.string.dialog_clone_via_google_play_explanation, source);
				break;
			case CLONE_RESULT_UNKNOWN_SYS_MARKET:
				final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				final ActivityInfo market_info = market_intent.resolveActivityInfo(context.getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY);
				if (market_info != null && (market_info.applicationInfo.flags & FLAG_SYSTEM) != 0)
					Analytics.$().event("clone_via_market").with(ITEM_ID, pkg).with(ITEM_CATEGORY, market_info.packageName).send();
				showExplanationBeforeCloning("clone-via-sys-market-explained", context, R.string.dialog_clone_via_sys_market_explanation, source);
				break;
			case CLONE_RESULT_NOT_FOUND:
				Toast.makeText(context, R.string.toast_internal_error, Toast.LENGTH_SHORT).show();
				break;
			}
		}).exceptionally(t -> {
			reportAndShowToastForInternalException(context, "Error checking user app for cloning: " + pkg, t);
			return null;
		});	// Dry run to check prerequisites.
	}

	private static void cloneAppViaInstaller(final Context context, final IslandAppInfo source) {
		showExplanationBeforeCloning("clone-via-install-explained", context, R.string.dialog_clone_via_install_explanation, source);
	}

	private static void cloneAppViaRootWithFallback(final Context context, final IslandAppInfo source, final UserHandle profile, final BiConsumer<Context, IslandAppInfo> fallback) {
		final String pkg = source.packageName;
		final String cmd = "cmd package install-existing --user " + Users.toId(profile) + " " + pkg;	// Try root approach first
		new AsyncTask<Void, Void, List<String>>() {
			@Override protected List<String> doInBackground(final Void[] params) {
				return Shell.SU.run(cmd);
			}

			@RequiresApi(O) @Override protected void onPostExecute(final List<String> result) {
				try {
					final ApplicationInfo app_info = context.getSystemService(LauncherApps.class).getApplicationInfo(pkg, 0, profile);
					if (app_info != null && (app_info.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
						Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.getLabel()), Toast.LENGTH_SHORT).show();
						return;
					}
				} catch (final PackageManager.NameNotFoundException e) {
					Log.i(TAG, "Failed to clone app via root: " + pkg);
					if (result != null && ! result.isEmpty()) Analytics.$().logAndReport(TAG, "Error executing: " + cmd,
							new ExecutionException("ROOT: " + cmd + ", result: " + String.join(" \\n ", result), null));
				}
				fallback.accept(context, source);
			}
		}.execute();
	}

	@OwnerUser private static void doCloneUserApp(final Context context, final IslandAppInfo source) {
		@SuppressWarnings("UnnecessaryLocalVariable") final ApplicationInfo info = source;	// To keep type consistency in the following method shuttle
		MethodShuttle.runInProfile(context, () -> new IslandAppClones(context).performUserAppCloning(info.packageName, info, true)).thenAccept(result -> {
			switch (result) {
			case CLONE_RESULT_OK_INSTALL_EXISTING:		// Visual feedback for instant cloning.
				Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.getLabel()), Toast.LENGTH_SHORT).show();
				break;
			case CLONE_RESULT_OK_INSTALL:
			case CLONE_RESULT_OK_GOOGLE_PLAY:
			case CLONE_RESULT_UNKNOWN_SYS_MARKET:
				break;		// Expected result
			case CLONE_RESULT_NOT_FOUND:
			case CLONE_RESULT_ALREADY_CLONED:
			case CLONE_RESULT_NO_SYS_MARKET:
				Log.e(TAG, "Unexpected cloning result: " + result);
				break;
			}
		}).exceptionally(t -> {
			reportAndShowToastForInternalException(context, "Error cloning user app: " + source.packageName, t);
			return null;
		});
	}

	private static void showExplanationBeforeCloning(final String mark, final Context context, final @StringRes int explanation, final IslandAppInfo source) {
		final Activity activity = Activities.findActivityFrom(context);
		if (activity != null && ! activity.isFinishing() && ! Scopes.app(context).isMarked(mark)) {
			Dialogs.buildAlert(activity, 0, explanation).setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
				Scopes.app(context).markOnly(mark);
				doCloneUserApp(context, source);
			}).show();
		} else doCloneUserApp(context, source);
	}

	/** Two-stage operation, because of pre-cloning user interaction, depending on the situation in managed profile. */
	@ProfileUser private int performUserAppCloning(final String pkg, final ApplicationInfo app_info, final boolean do_it) {
		// Blindly clear these restrictions
		mDevicePolicies.clearUserRestrictionsIfNeeded(mContext, UserManager.DISALLOW_INSTALL_APPS);

		if (SDK_INT >= P && mContext.getSystemService(DevicePolicyManager.class).isAffiliatedUser()) try {
			if (! do_it) return CLONE_RESULT_OK_INSTALL_EXISTING;
			if (mDevicePolicies.invoke(DevicePolicyManager::installExistingPackage, pkg))
				return CLONE_RESULT_OK_INSTALL_EXISTING;
			Log.e(TAG, "Error cloning existent user app: " + pkg);								// Fall-through
		} catch (final SecurityException e) {
			Analytics.$().logAndReport(TAG, "Error cloning existent user app: " + pkg, e);	// Fall-through
		}

		if (! IslandManager.ensureLegacyInstallNonMarketAppAllowed(mContext, mDevicePolicies))
			return cloneUserAppViaMarketApp(pkg, do_it);

		final String my_pkg = mContext.getPackageName();
		final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts(SCHEME_PACKAGE, pkg, null))
				.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, my_pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mDevicePolicies.enableSystemApp(intent);				// Ensure package installer is enabled.

		if (! do_it) return CLONE_RESULT_OK_INSTALL;

		mContext.startActivity(intent.addCategory(mContext.getPackageName()).putExtra(InstallerExtras.EXTRA_APP_INFO, app_info));	// Launch App Installer
		return CLONE_RESULT_OK_INSTALL;
	}

	private int cloneUserAppViaMarketApp(final String pkg, final boolean do_it) {
		// Launch market app (preferable Google Play Store)
		final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mDevicePolicies.enableSystemApp(market_intent);
		final ResolveInfo market_info = mContext.getPackageManager().resolveActivity(market_intent, SDK_INT < N ? 0 : MATCH_SYSTEM_ONLY);
		if (market_info == null || (market_info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) == 0)	// Only privileged app market could install. (TODO: Should check "privileged" instead of system)
			return CLONE_RESULT_NO_SYS_MARKET;

		if (WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE.equals(market_info.activityInfo.applicationInfo.packageName)) {
			if (do_it) {
				mDevicePolicies.enableSystemApp(WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES);	// Special dependency
				mContext.startActivity(market_intent);
			}
			return CLONE_RESULT_OK_GOOGLE_PLAY;
		} else {
			if (do_it) mContext.startActivity(market_intent);
			return CLONE_RESULT_UNKNOWN_SYS_MARKET;
		}
	}

	private static void reportAndShowToastForInternalException(final Context context, final String log, final Throwable t) {
		Analytics.$().logAndReport(TAG, log, t);
		Toast.makeText(context, "Internal error: " + t.getMessage(), Toast.LENGTH_LONG).show();
	}

	private IslandAppClones(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;

	private static final String TAG = "Island.IC";
}
