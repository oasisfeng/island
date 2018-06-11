package com.oasisfeng.island.model;

import android.app.Activity;
import android.app.AlertDialog;
import android.arch.lifecycle.MutableLiveData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.BottomSheetBehavior;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.common.app.BaseAppListViewModel;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.featured.FeaturedListViewModel;
import com.oasisfeng.island.greenify.GreenifyClient;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.shortcut.AbstractAppLaunchShortcut;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import java9.util.Optional;
import java9.util.function.BooleanSupplier;
import java9.util.function.Predicate;
import java9.util.stream.Collectors;

import static android.content.Intent.EXTRA_INITIAL_INTENTS;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/**
 * View model for apps
 *
 * Created by Oasis on 2015/7/7.
 */
@ParametersAreNonnullByDefault
public class AppListViewModel extends BaseAppListViewModel<AppViewModel> {

	private static final long QUERY_TEXT_DELAY = 300;	// The delay before typed query text is applied
	private static final String STATE_KEY_FILTER_PRIMARY_CHOICE = "filter.primary";
	private static final String ACTION_SHOW_APP_INFO = "android.intent.action.SHOW_APP_INFO";	// clone of Intent.ACTION_SHOW_APP_INFO
	private static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";		// clone of Intent.EXTRA_PACKAGE_NAME

	/** Workaround for menu res reference not supported by data binding */ public static @MenuRes int actions_menu = R.menu.app_actions;

	@SuppressWarnings("WeakerAccess") public enum Filter {
		Island		(Users::hasProfile,  app -> Users.isProfile(app.user) && app.shouldShowAsEnabled()),
		Mainland	(() -> true,         app -> Users.isOwner(app.user)),
		;
		boolean available() { return mVisibility.getAsBoolean(); }
		Filter(final BooleanSupplier visibility, final Predicate<IslandAppInfo> filter) { mVisibility = visibility; mFilter = filter; }

		private final BooleanSupplier mVisibility;
		private final Predicate<IslandAppInfo> mFilter;
	}

	public boolean areSystemAppsIncluded() { return mFilterIncludeHiddenSystemApps; }

	private Predicate<IslandAppInfo> activeFilters() {
		return mActiveFilters;
	}

	public void onFilterHiddenSysAppsInclusionChanged(final boolean should_include) {
		mFilterIncludeHiddenSystemApps = should_include;
		updateActiveFilters();
	}

	public void onQueryTextChange(final String text) {
		if (TextUtils.equals(text, mFilterText)) return;

		mHandler.removeCallbacks(mQueryTextDelayer);
		mFilterText = text;
		if (TextUtils.isEmpty(text)) mQueryTextDelayer.run();
		else mHandler.postDelayed(mQueryTextDelayer, QUERY_TEXT_DELAY);		// A short delay to avoid flickering during typing.
	}
	private final Runnable mQueryTextDelayer = this::updateActiveFilters;

	private boolean matchQueryText(final IslandAppInfo app) {
		final String text_lc = mFilterText.toLowerCase();
		return app.packageName.toLowerCase().contains(text_lc) || app.getLabel().toLowerCase().contains(text_lc);	// TODO: Support T9 Pinyin
	}

	private void updateActiveFilters() {
		if (mFilterShared == null) return;		// When called by constructor
		final Filter primary_filter = mPrimaryFilter.getValue();
		if (primary_filter == null) return;
		Log.d(TAG, "Primary filter: " + primary_filter);
		Predicate<IslandAppInfo> combined_filter = mFilterShared.and(primary_filter.mFilter);
		if (! mFilterIncludeHiddenSystemApps) combined_filter = combined_filter.and(app -> ! app.isSystem() || app.isLaunchable());
		if (! TextUtils.isEmpty(mFilterText)) combined_filter = combined_filter.and(this::matchQueryText);
		mActiveFilters = combined_filter;

		final AppViewModel selected = mSelection.getValue();
		clearSelection();

		IslandAppInfo.startBatchLauncherActivityCheck(Objects.requireNonNull(mAppListProvider.getContext()));	// Performance optimization
		final List<AppViewModel> apps = mAppListProvider.installedApps().filter(activeFilters()).map(AppViewModel::new).collect(Collectors.toList());
		IslandAppInfo.endBatchLauncherActivityCheck();
		replaceApps(apps);

		if (selected != null) for (final AppViewModel app : apps)
			if (app.info().packageName.equals(selected.info().packageName)) {
				setSelection(app);
				break;
			}
	}

	public AppListViewModel() {
		super(AppViewModel.class);
		mSelection.observeForever(selection -> updateActions());
	}

	public boolean onTabSwitched(final Context context, final MenuItem tab) {
		final int tab_menu_id = tab.getItemId();
		if (tab_menu_id == R.id.tab_discovery) {
			mPrimaryFilter.setValue(null);
			mFeatured.visible.setValue(Boolean.TRUE);
			mFeatured.update(context);
			return true;
		} else mFeatured.visible.setValue(Boolean.FALSE);

		if (tab_menu_id == R.id.tab_island) {
			if (! Filter.Island.available()) return false;
			mPrimaryFilter.setValue(Filter.Island);
		} else if (tab_menu_id == R.id.tab_mainland) {
			mPrimaryFilter.setValue(Filter.Mainland);
		} else return false;
		updateActiveFilters();
		return true;
	}

	public void attach(final Context context, final Menu actions, final BottomNavigationView tabs, final @Nullable Bundle saved_state) {
		mAppListProvider = IslandAppListProvider.getInstance(context);
		mDeviceOwner = new DevicePolicies(context).isActiveDeviceOwner();
		mActions = actions;
		mFilterShared = IslandAppListProvider.excludeSelf(context).and(AppInfo::isInstalled);
		mPrimaryFilter.observeForever(filter -> updateActiveFilters());

		if (! Filter.Island.available()) {		// Island is unavailable
			tabs.getMenu().findItem(R.id.tab_island).setVisible(false);
			tabs.setSelectedItemId(R.id.tab_mainland);
		} else {
			final int ordinal = Optional.ofNullable(saved_state).map(s -> s.getInt(STATE_KEY_FILTER_PRIMARY_CHOICE)).orElse(Filter.Island.ordinal()/* default */);
			final Filter primary_filter = Filter.values()[ordinal];
			tabs.setSelectedItemId(primary_filter == Filter.Mainland ? R.id.tab_mainland : R.id.tab_island);
			mPrimaryFilter.setValue(primary_filter);
		}
	}

	public void setOwnerController(final IIslandManager controller) {
		mOwnerController = controller;
	}

	public void onSaveInstanceState(final Bundle saved) {
		final Filter primary_filter = mPrimaryFilter.getValue();
		if (primary_filter != null) saved.putInt(STATE_KEY_FILTER_PRIMARY_CHOICE, primary_filter.ordinal());
	}

	private void updateActions() {
		final AppViewModel selection = mSelection.getValue();
		if (selection == null) return;
		final IslandAppInfo app = selection.info();
		final UserHandle profile = Users.profile;
		final boolean exclusive = mAppListProvider.isExclusive(app);

		final boolean in_owner = Users.isOwner(app.user), is_managed = mDeviceOwner || ! in_owner;
		mActions.findItem(R.id.menu_freeze).setVisible(is_managed && ! app.isHidden() && app.enabled);
		mActions.findItem(R.id.menu_unfreeze).setVisible(is_managed && app.isHidden());
		mActions.findItem(R.id.menu_clone).setVisible(in_owner && profile != null && exclusive);
		mActions.findItem(R.id.menu_clone_back).setVisible(! in_owner && exclusive);
		final boolean system = app.isSystem();
		mActions.findItem(R.id.menu_remove).setVisible(exclusive ? system : (! system || app.shouldShowAsEnabled()));	// Disabled system app is treated as "removed".
		mActions.findItem(R.id.menu_uninstall).setVisible(exclusive && ! system);	// "Uninstall" for exclusive user app, "Remove" for exclusive system app.
		mActions.findItem(R.id.menu_shortcut).setVisible(is_managed && app.isLaunchable() && app.enabled);
		mActions.findItem(R.id.menu_greenify).setVisible(is_managed && app.enabled);
	}

	public void onPackagesUpdate(final Collection<IslandAppInfo> apps) {
		final Predicate<IslandAppInfo> filters = activeFilters();
		for (final IslandAppInfo app : apps)
			if (filters.test(app)) {
				putApp(app.packageName, new AppViewModel(app));
			} else removeApp(app.packageName, app.user);
		updateActions();
	}

	private void removeApp(final String pkg, final UserHandle user) {
		final AppViewModel app = getApp(pkg);
		if (app != null && app.info().user.equals(user)) super.removeApp(pkg);
	}

	public void onPackagesRemoved(final Collection<IslandAppInfo> apps) {
		final Predicate<IslandAppInfo> filters = activeFilters();
		for (final IslandAppInfo app : apps)
			if (filters.test(app)) removeApp(app.packageName);
		updateActions();
	}

	public final void onItemLaunchIconClick(@SuppressWarnings("UnusedParameters") final View v) {
		if (mSelection.getValue() == null) return;
		final IslandAppInfo app = mSelection.getValue().info();
		Analytics.$().event("action_launch").with(ITEM_ID, app.packageName).send();
		if (! app.isHidden() && IslandManager.launchApp(v.getContext(), app.packageName, app.user)) return;	// Not frozen, launch the app directly.
		String failure;
		try {
			failure = controller(app).launchApp(app.packageName);
		} catch (final RemoteException e) {
			failure = e.toString();
		}
		if (failure != null) {
			Analytics.$().event("app_launch_error").with(ITEM_ID, app.packageName).with(ITEM_CATEGORY, failure).send();
			Toast.makeText(v.getContext(), R.string.toast_failed_to_launch_app, Toast.LENGTH_SHORT).show();
		}
	}

	public boolean onActionClick(final Context context, final MenuItem item) {
		final AppViewModel selection = mSelection.getValue();
		if (selection == null) return false;
		final IslandAppInfo app = selection.info();
		final String pkg = app.packageName;
		final IIslandManager controller = controller(app);

		final int id = item.getItemId();
		if (id == R.id.menu_clone) {
			cloneApp(context, app);
			clearSelection();
		} else if (id == R.id.menu_clone_back) {
			Activities.startActivity(context, new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null)));
			Analytics.$().event("action_install_outside").with(ITEM_ID, pkg).send();
			clearSelection();
		} else if (id == R.id.menu_freeze) {// Select the next alive app, or clear selection.
			Analytics.$().event("action_freeze").with(ITEM_ID, pkg).send();

			final Activity activity = Activities.findActivityFrom(context);
			if (activity != null && IslandAppListProvider.getInstance(context).isCritical(pkg)) {
				Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.dialog_critical_app_warning)
						.withCancelButton().withOkButton(() -> freezeApp(context, selection)).show();
			} else freezeApp(context, selection);
		} else if (id == R.id.menu_unfreeze) {
			Analytics.$().event("action_unfreeze").with(ITEM_ID, pkg).send();
			try {
				controller.unfreezeApp(pkg);
				refreshAppStateAsSysBugWorkaround(context, pkg);
				clearSelection();
			} catch (final RemoteException ignored) {}
		} else if (id == R.id.menu_app_settings) {
			launchExternalAppSettings(context, app);
		} else if (id == R.id.menu_remove || id == R.id.menu_uninstall) {
			onRemovalRequested(context);
		} else if (id == R.id.menu_shortcut) {
			onShortcutRequested(context);
		} else if (id == R.id.menu_greenify) {
			onGreenifyRequested(context);
//		} else if (id == R.id.menu_enable) {
//			final LauncherApps launcher_apps = (LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
//			launcher_apps.startAppDetailsActivity(new ComponentName(pkg, ""), selection.info().user, null, null);
		}
		return true;
	}

	private void freezeApp(final Context context, final AppViewModel app_vm) {
		// Select the next app for convenient continuous freezing.
		final int next_index = indexOf(app_vm) + 1;
		final AppViewModel next;
		if (next_index < size() && (next = getAppAt(next_index)).state == State.Alive) setSelection(next);
		else clearSelection();

		final IslandAppInfo app = app_vm.info();
		try {
			final boolean frozen = controller(app).freezeApp(app.packageName, "manual");
			if (frozen) app.stopTreatingHiddenSysAppAsDisabled();
			else Toast.makeText(context, R.string.toast_error_freeze_failure, Toast.LENGTH_LONG).show();
			refreshAppStateAsSysBugWorkaround(context, app.packageName);
		} catch (final RemoteException e) {
			Toast.makeText(context, "Internal error: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void launchSystemAppSettings(final Context context, final IslandAppInfo app) {
		try {
			if (app.isHidden()) controller(app).unfreezeApp(app.packageName);	// Stock app info activity requires the app not hidden.
			Objects.requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE))
					.startAppDetailsActivity(new ComponentName(app.packageName, ""), app.user, null, null);
		} catch (final RemoteException | SecurityException e) {
			Toast.makeText(context, "Internal error: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void launchExternalAppSettings(final Context context, final IslandAppInfo app) {
		final Intent target = new Intent(ACTION_SHOW_APP_INFO).putExtra(EXTRA_PACKAGE_NAME, app.packageName);
		if (! Users.isOwner(app.user) || context.getPackageManager().queryIntentActivities(target, 0).isEmpty()) {
			launchSystemAppSettings(context, app);
			return;
		}

		try {
			if (app.isHidden()) controller(app).unfreezeApp(app.packageName);	// Stock app info activity requires the app not hidden.
		} catch (final RemoteException | SecurityException e) {
			Toast.makeText(context, "Internal error: " + e.getMessage(), Toast.LENGTH_LONG).show();
			return;
		}

		final Intent chooser = new Intent(Intent.ACTION_CHOOSER).putExtra(Intent.EXTRA_INTENT, target);
		chooser.putExtra(EXTRA_INITIAL_INTENTS, new Parcelable[] { new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
				Uri.fromParts("package", app.packageName, null)) });
		Activities.startActivity(context, chooser);
	}

	private void onShortcutRequested(final Context context) {
		if (mSelection.getValue() == null) return;
		final String pkg = mSelection.getValue().info().packageName;
		Analytics.$().event("action_create_shortcut").with(ITEM_ID, pkg).send();
		final String shortcut_prefix = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_launch_shortcut_prefix), context.getString(R.string.default_launch_shortcut_prefix));
		final Boolean result = AbstractAppLaunchShortcut.createOnLauncher(context, pkg, Users.isOwner(mSelection.getValue().info().user), shortcut_prefix);
		if (result == null) Toast.makeText(context, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();	// No toast if result == true, since the shortcut pinning is pending user confirmation.
		else if (! result) Toast.makeText(context, R.string.toast_shortcut_failed, Toast.LENGTH_LONG).show();
	}

	private void onGreenifyRequested(final Context context) {
		if (mSelection.getValue() == null) return;
		final IslandAppInfo app = mSelection.getValue().info();
		Analytics.$().event("action_greenify").with(ITEM_ID, app.packageName).send();

		final String mark = "greenify-explained";
		final Boolean greenify_ready = GreenifyClient.checkGreenifyVersion(context);
		final boolean greenify_installed = greenify_ready != null;
		final boolean unavailable_or_version_too_low = greenify_ready == null || ! greenify_ready;
		if (unavailable_or_version_too_low || ! Scopes.app(context).isMarked(mark)) {
			String message = context.getString(R.string.dialog_greenify_explanation);
			if (greenify_installed && unavailable_or_version_too_low)
				message += "\n\n" + context.getString(R.string.dialog_greenify_version_too_low);
			final int button = ! greenify_installed ? R.string.dialog_button_install : ! greenify_ready ? R.string.dialog_button_upgrade : R.string.dialog_button_continue;
			new AlertDialog.Builder(context).setTitle(R.string.dialog_greenify_title).setMessage(message).setPositiveButton(button, (d, w) -> {
				if (! unavailable_or_version_too_low) {
					Scopes.app(context).markOnly(mark);
					greenify(context, app);
				} else GreenifyClient.openInAppMarket(context);
			}).show();
		} else greenify(context, app);
	}

	private static void greenify(final Context context, final IslandAppInfo app) {
		if (! GreenifyClient.greenify(context, app.packageName, app.user))
			Toast.makeText(context, R.string.toast_greenify_failed, Toast.LENGTH_LONG).show();
	}

	public void onBlockingRequested() {
		if (mSelection.getValue() == null) return;
		try {
			controller(mSelection.getValue().info()).block(mSelection.getValue().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	public void onUnblockingRequested() {
		if (mSelection.getValue() == null) return;
		try {
			controller(mSelection.getValue().info()).unblock(mSelection.getValue().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	private void onRemovalRequested(final Context context) {
		if (mSelection.getValue() == null) return;
		final IslandAppInfo app = mSelection.getValue().info();
		Analytics.$().event("action_uninstall").with(ITEM_ID, app.packageName).with(ITEM_CATEGORY, "system").send();
		if (app.isSystem()) {
			final Activity activity = Objects.requireNonNull(Activities.findActivityFrom(context));
			if (app.isCritical()) {
				Dialogs.buildAlert(activity, R.string.dialog_title_warning, R.string.dialog_critical_app_warning).withCancelButton()
						.setPositiveButton(R.string.dialog_button_continue, (d, w) -> launchSystemAppSettings(context, app)).show();
			} else Dialogs.buildAlert(activity, 0, R.string.prompt_disable_sys_app_as_removal).withCancelButton()
					.setPositiveButton(R.string.dialog_button_continue, (d, w) -> launchSystemAppSettings(context, app)).show();
		} else try {
			if (app.isHidden()) controller(app).unfreezeApp(app.packageName);	// Unfreeze it first, otherwise we cannot receive the package removal event.
			if (app.isSystem()) {
				final LauncherApps launcher = Objects.requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE));
				launcher.startAppDetailsActivity(new ComponentName(app.packageName, ""), app.user, null, null);
				Analytics.$().event("action_disable_sys_app").with(ITEM_ID, app.packageName).send();
			} else Activities.startActivity(context, new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.fromParts("package", app.packageName, null))
					.putExtra(Intent.EXTRA_USER, app.user));
		} catch (final RemoteException ignored) {
			final LauncherApps launcher = Objects.requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE));
			launcher.startAppDetailsActivity(new ComponentName(app.packageName, ""), Users.profile, null, null);
			Toast.makeText(context, "Click \"Uninstall\" to remove the clone.", Toast.LENGTH_LONG).show();
		}
	}

	/** Possible 10s delay before the change broadcast could be received (due to Android issue 225880), so we force a refresh immediately. */
	private static void refreshAppStateAsSysBugWorkaround(final Context context, final String pkg) {
		IslandAppListProvider.getInstance(context).refreshPackage(pkg, Users.profile, false);
	}

	private void cloneApp(final Context context, final IslandAppInfo app) {
		final int check_result;
		final String pkg = app.packageName;
		final IslandAppInfo app_in_profile = IslandAppListProvider.getInstance(context).get(app.packageName, Users.profile);
		if (app_in_profile != null && app_in_profile.isInstalled() && ! app_in_profile.enabled) {
			launchSystemAppSettings(context, app_in_profile);
			return;
		}

		try {
			check_result = mProfileController.cloneApp(pkg, false);
		} catch (final RemoteException ignored) { return; }		// FIXME: Error message
		switch (check_result) {
		case IslandManager.CLONE_RESULT_NOT_FOUND:    			// FIXME: Error message
			Toast.makeText(context, R.string.toast_internal_error, Toast.LENGTH_SHORT).show();
			return;
		case IslandManager.CLONE_RESULT_ALREADY_CLONED:
			if (app_in_profile != null && ! app_in_profile.shouldShowAsEnabled()) {	// Actually frozen system app shown as disabled, just unfreeze it.
				try {
					if (mProfileController.unfreezeApp(pkg)) {
						app.stopTreatingHiddenSysAppAsDisabled();
						Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, app.getLabel()), Toast.LENGTH_SHORT).show();
					}
				} catch (final RemoteException ignored) {}    	// FIXME: Error message
			} else Toast.makeText(context, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show();
			return;
		case IslandManager.CLONE_RESULT_NO_SYS_MARKET:
			final Activity activity = Activities.findActivityFrom(context);
			if (activity != null) Dialogs.buildAlert(activity, 0, R.string.dialog_clone_incapable_explanation)
					.setNeutralButton(R.string.dialog_button_learn_more, (d, w) -> WebContent.view(context, Config.URL_FAQ.get()))
					.setPositiveButton(android.R.string.cancel, null).show();
			else Toast.makeText(context, R.string.dialog_clone_incapable_explanation, Toast.LENGTH_LONG).show();
			return;
		case IslandManager.CLONE_RESULT_OK_SYS_APP:
			Analytics.$().event("clone_sys").with(ITEM_ID, pkg).send();
			doCloneApp(context, app);
			break;
		case IslandManager.CLONE_RESULT_OK_INSTALL:
			Analytics.$().event("clone_install").with(ITEM_ID, pkg).send();
			showExplanationBeforeCloning("clone-via-install-explained", context, R.string.dialog_clone_via_install_explanation, app);
			break;
		case IslandManager.CLONE_RESULT_OK_GOOGLE_PLAY:
			Analytics.$().event("clone_via_play").with(ITEM_ID, pkg).send();
			showExplanationBeforeCloning("clone-via-google-play-explained", context, R.string.dialog_clone_via_google_play_explanation, app);
			break;
		case IslandManager.CLONE_RESULT_UNKNOWN_SYS_MARKET:
			final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			final ActivityInfo market_info = market_intent.resolveActivityInfo(context.getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY);
			if (market_info != null && (market_info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				Analytics.$().event("clone_via_market").with(ITEM_ID, pkg).with(ITEM_CATEGORY, market_info.packageName).send();
			showExplanationBeforeCloning("clone-via-sys-market-explained", context, R.string.dialog_clone_via_sys_market_explanation, app);
			break;
		}
	}

	private void doCloneApp(final Context context, final IslandAppInfo app) {
		final int result; try {
			result = mProfileController.cloneApp(app.packageName, true);
		} catch (final RemoteException ignored) { return; }	// FIXME: Error message
		switch (result) {
		case IslandManager.CLONE_RESULT_OK_SYS_APP:		// Need visual feedback since the just finished procedure is completely silent.
			Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, app.getLabel()), Toast.LENGTH_SHORT).show();
		case IslandManager.CLONE_RESULT_OK_INSTALL:
		case IslandManager.CLONE_RESULT_OK_GOOGLE_PLAY:
		case IslandManager.CLONE_RESULT_UNKNOWN_SYS_MARKET:
			return;		// Expected result
		case IslandManager.CLONE_RESULT_NOT_FOUND:
		case IslandManager.CLONE_RESULT_ALREADY_CLONED:
		case IslandManager.CLONE_RESULT_NO_SYS_MARKET:
			Log.e(TAG, "Unexpected cloning result: " + result);
		}
	}

	private void showExplanationBeforeCloning(final String mark, final Context context, final @StringRes int explanation, final IslandAppInfo app) {
		final Activity activity = Activities.findActivityFrom(context);
		if (activity != null && ! Scopes.app(context).isMarked(mark)) {
			Dialogs.buildAlert(activity, 0, explanation).setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
				Scopes.app(context).markOnly(mark);
				doCloneApp(context, app);
			}).show();
		} else doCloneApp(context, app);
	}

	public final void onItemClick(final AppViewModel clicked) {
		setSelection(clicked != mSelection.getValue() ? clicked : null);	// Click the selected one to deselect
	}

	@SuppressWarnings("MethodMayBeStatic") public final void onBottomSheetClick(final View view) {
		final BottomSheetBehavior bottom_sheet = BottomSheetBehavior.from(view);
		bottom_sheet.setState(BottomSheetBehavior.STATE_EXPANDED);
	}

	private IIslandManager controller(final IslandAppInfo app) {
		return Users.isOwner(app.user) ? mOwnerController : mProfileController;
	}

	/* Parcelable */

	public final BottomSheetBehavior.BottomSheetCallback bottom_sheet_callback = new BottomSheetBehavior.BottomSheetCallback() {

		@Override public void onStateChanged(@NonNull final View bottom_sheet, final int new_state) {
			if (new_state == BottomSheetBehavior.STATE_HIDDEN) clearSelection();
		}

		@Override public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {}
	};

	public final ItemBinder<AppViewModel> item_binder = (container, model, item) -> {
		item.setVariable(BR.app, model);
		item.setVariable(BR.apps, this);
	};

	public FeaturedListViewModel mFeatured;
	/* Attachable fields */
	private IslandAppListProvider mAppListProvider;
	private Menu mActions;
	private IIslandManager mOwnerController;
	public IIslandManager mProfileController;
	/* Parcelable fields */
	public final MutableLiveData<Filter> mPrimaryFilter = new MutableLiveData<>();
	private boolean mFilterIncludeHiddenSystemApps;
	/* Transient fields */
	private Predicate<IslandAppInfo> mFilterShared;		// All other filters to apply always
	private String mFilterText;
	private boolean mDeviceOwner;
	private Predicate<IslandAppInfo> mActiveFilters;		// The active composite filters
	private final Handler mHandler = new Handler();

	private static final String TAG = "Island.Apps";
}
