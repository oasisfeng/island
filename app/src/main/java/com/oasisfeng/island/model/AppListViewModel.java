package com.oasisfeng.island.model;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.databinding.ViewDataBinding;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomSheetBehavior;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.common.app.BaseAppListViewModel;
import com.oasisfeng.island.BR;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.R;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.databinding.AppEntryBinding;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.greenify.GreenifyClient;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;
import com.oasisfeng.island.util.Users;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import java8.util.function.BooleanSupplier;
import java8.util.function.Predicate;
import java8.util.function.Predicates;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

/**
 * View model for apps
 *
 * Created by Oasis on 2015/7/7.
 */
public class AppListViewModel extends BaseAppListViewModel<AppViewModel> implements Parcelable {

	public static final Predicate<IslandAppInfo> NON_SYSTEM = app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
	public static final Predicate<IslandAppInfo> NON_HIDDEN_SYSTEM = app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || app.isLaunchable();

	/** Workaround for menu res reference not supported by data binding */ public static @MenuRes int actions_menu = R.menu.app_actions;

	@SuppressWarnings("unused") public enum Filter {
		Island		(R.string.filter_island,    GlobalStatus::hasProfile,  app -> Users.isProfile(app.user) && app.isInstalled() && app.shouldTreatAsEnabled()),
		Mainland	(R.string.filter_mainland,  () -> true,                app -> Users.isOwner(app.user) && app.isInstalled()),
		;
		boolean visible() { return mVisibility.getAsBoolean(); }
		Filter(final @StringRes int label, final BooleanSupplier visibility, final Predicate<IslandAppInfo> filter) { mLabel = label; mVisibility = visibility; mFilter = filter; }

		private final @StringRes int mLabel;
		private final BooleanSupplier mVisibility;
		private final Predicate<IslandAppInfo> mFilter;

		public class Entry {
			Entry(final Context context) { mContext = context; }
			Predicate<IslandAppInfo> filter() { return mFilter; }
			@Override public String toString() { return mContext.getString(mLabel); }
			private final Context mContext;
		}
	}

	public boolean areSystemAppsIncluded() { return mFilterIncludeSystemApps; }

	private Predicate<IslandAppInfo> activeFilters() {
		return mActiveFilters;
	}

	public int getFilterPrimaryChoice() { return mFilterPrimaryChoice; }

	public void onFilterPrimaryChanged(final int index) {
		if (mActiveFilters != null && mFilterPrimaryChoice == index) return;
		mFilterPrimaryChoice = index;
		updateActiveFilters();
		rebuildAppViewModels();
	}

	public void onFilterHiddenSysAppsInclusionChanged(final boolean should_include) {
		mFilterIncludeSystemApps = should_include;
		updateActiveFilters();
		rebuildAppViewModels();
	}

	private void updateActiveFilters() {
		final Predicate<IslandAppInfo> primary_with_extras = Predicates.and(mFilterExtras, mFilterPrimaryOptions.get(mFilterPrimaryChoice).filter());
		mActiveFilters = mFilterIncludeSystemApps ? primary_with_extras : Predicates.and(primary_with_extras, NON_HIDDEN_SYSTEM);
	}

	private void rebuildAppViewModels() {
		clearSelection();
		final List<AppViewModel> apps = IslandAppListProvider.getInstance(mActivity).installedApps()
				.filter(activeFilters()).map(AppViewModel::new).collect(Collectors.toList());
		replaceApps(apps);
	}

	public AppListViewModel() {
		super(AppViewModel.class);
		addOnPropertyChangedCallback(new OnPropertyChangedCallback() { @Override public void onPropertyChanged(final Observable sender, final int property) {
			if (property == BR.selection) updateActions();
		}});
	}

	public void attach(final Activity activity, final IIslandManager owner_controller, final Menu actions, final int filter_primary_choice) {
		mActivity = activity;
		mOwnerController = owner_controller;
		mActions = actions;
		mFilterPrimaryOptions = StreamSupport.stream(Arrays.asList(Filter.values())).filter(Filter::visible).map(filter -> filter.new Entry(activity)).collect(Collectors.toList());
		mFilterExtras = Predicates.and(IslandAppListProvider.excludeSelf(activity),
				app -> app.isInstalled() && (Users.isOwner(app.user) || app.shouldTreatAsEnabled()));	// Exclude disabled sys-apps in profile
		onFilterPrimaryChanged(filter_primary_choice);
	}

	private void updateActions() {
		final AppViewModel selection = getSelection();
		if (selection == null) return;
		final IslandAppInfo app = selection.info();
		final UserHandle profile = GlobalStatus.profile;
		final IslandAppListProvider provider = IslandAppListProvider.getInstance(mActivity);
		final boolean exclusive = provider.isExclusive(app);

		mActions.findItem(R.id.menu_freeze).setVisible(! app.isHidden() && app.enabled);
		mActions.findItem(R.id.menu_unfreeze).setVisible(app.isHidden());
		mActions.findItem(R.id.menu_clone).setVisible(profile != null && exclusive);
		mActions.findItem(R.id.menu_remove).setVisible(! exclusive && (! app.isSystem() || app.shouldTreatAsEnabled()));	// Disabled system app is treated as "removed".
		mActions.findItem(R.id.menu_uninstall).setVisible(exclusive && ! app.isSystem());
		mActions.findItem(R.id.menu_shortcut).setVisible(app.isLaunchable() && app.enabled);
		mActions.findItem(R.id.menu_greenify).setVisible(app.enabled);
	}

	public void onPackagesUpdate(final Collection<IslandAppInfo> apps) {
		final Predicate<IslandAppInfo> filters = activeFilters();
		for (final IslandAppInfo app : apps) {
			if (filters.test(app)) {
				putApp(app.packageName, new AppViewModel(app));
			} else if (app.getLastInfo() != null && filters.test(app.getLastInfo()))
				removeApp(app.packageName);
		}
		updateActions();
	}

	public void onPackagesRemoved(final Collection<IslandAppInfo> apps) {
		final Predicate<IslandAppInfo> filters = activeFilters();
		for (final IslandAppInfo app : apps)
			if (filters.test(app)) removeApp(app.packageName);
		updateActions();
	}

	public final void onItemLaunchIconClick(final View v) {
		if (getSelection() == null) return;
		final IslandAppInfo app = getSelection().info();
		Analytics.$().event("action_launch").with("package", app.packageName).send();
		try {
			controller(app).launchApp(app.packageName);
		} catch (final RemoteException ignored) {}
	}

	public boolean onActionClick(final MenuItem item) {
		final AppViewModel selection = getSelection();
		if (selection == null) return false;
		final IslandAppInfo app = selection.info();
		final String pkg = app.packageName;
		final IIslandManager controller = controller(app);

		switch (item.getItemId()) {
		case R.id.menu_clone:
			cloneApp(app);
			// Do not clear selection, for quick launch with one more click
			break;
		case R.id.menu_freeze:
			// Select the next alive app, or clear selection.
			final int next_index = indexOf(selection) + 1;
			if (next_index >= size()) clearSelection();
			else {
				final AppViewModel next = getAppAt(next_index);
				if (next.state == State.Alive)
					setSelection(next);
				else clearSelection();
			}
			Analytics.$().event("action_freeze").with("package", pkg).send();

			try {
				final boolean frozen = controller.freezeApp(pkg, "manual");
				if (! frozen) Toast.makeText(mActivity, R.string.toast_error_freeze_failure, Toast.LENGTH_LONG).show();
				refreshAppStateAsSysBugWorkaround(pkg);
			} catch (final RemoteException ignored) {
				Toast.makeText(mActivity, "Internal error", Toast.LENGTH_LONG).show();
			}
			break;
		case R.id.menu_unfreeze:
			Analytics.$().event("action_unfreeze").with("package", pkg).send();
			try {
				controller.unfreezeApp(pkg);
				refreshAppStateAsSysBugWorkaround(pkg);
				clearSelection();
			} catch (final RemoteException ignored) {}
			break;
		case R.id.menu_app_info:
			launchSettingsAppInfoActivity(app);
			break;
		case R.id.menu_remove:
		case R.id.menu_uninstall:
			onRemovalRequested();
			break;
		case R.id.menu_shortcut:
			onShortcutRequested();
			break;
		case R.id.menu_greenify:
			onGreenifyRequested();
			break;
//		case R.id.menu_enable:
//			final LauncherApps launcher_apps = (LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
//			launcher_apps.startAppDetailsActivity(new ComponentName(pkg, ""), selection.info().user, null, null);
//			break;
		}
		return true;
	}

	private void launchSettingsAppInfoActivity(final IslandAppInfo app) {
		try {
			controller(app).unfreezeApp(app.packageName);	// Stock app info activity requires the app not hidden.
			((LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE))
					.startAppDetailsActivity(new ComponentName(app.packageName, ""), app.user, null, null);
		} catch (final RemoteException ignored) {}
	}

	private void onShortcutRequested() {
		if (getSelection() == null) return;
		final String pkg = getSelection().info().packageName;
		Analytics.$().event("action_create_shortcut").with("package", pkg).send();
		if (AppLaunchShortcut.createOnLauncher(mActivity, pkg, Users.isOwner(getSelection().info().user))) {
			Toast.makeText(mActivity, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();
		} else Toast.makeText(mActivity, R.string.toast_shortcut_failed, Toast.LENGTH_SHORT).show();
	}

	private void onGreenifyRequested() {
		if (getSelection() == null) return;
		final IslandAppInfo app = getSelection().info();
		Analytics.$().event("action_greenify").with("package", app.packageName).send();

		final String mark = "greenify-explained";
		final Boolean greenify_ready = GreenifyClient.checkGreenifyVersion(mActivity);
		final boolean greenify_installed = greenify_ready != null;
		final boolean unavailable_or_version_too_low = greenify_ready == null || ! greenify_ready;
		if (unavailable_or_version_too_low || ! Scopes.app(mActivity).isMarked(mark)) {
			String message = mActivity.getString(R.string.dialog_greenify_explanation);
			if (greenify_installed && unavailable_or_version_too_low)
				message += "\n\n" + mActivity.getString(R.string.dialog_greenify_version_too_low);
			final int button = ! greenify_installed ? R.string.dialog_button_install : ! greenify_ready ? R.string.dialog_button_upgrade : R.string.dialog_button_continue;
			new AlertDialog.Builder(mActivity).setTitle(R.string.dialog_greenify_title).setMessage(message).setPositiveButton(button, (d, w) -> {
				if (! unavailable_or_version_too_low) {
					Scopes.app(mActivity).mark(mark);
					greenify(app);
				} else GreenifyClient.openInAppMarket(mActivity);
			}).show();
		} else greenify(app);
	}

	private void greenify(final IslandAppInfo app) {
		if (! GreenifyClient.greenify(mActivity, app.packageName, app.user))
			Toast.makeText(mActivity, R.string.toast_greenify_failed, Toast.LENGTH_LONG).show();
	}

	public void onBlockingRequested() {
		if (getSelection() == null) return;
		try {
			controller(getSelection().info()).block(getSelection().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	public void onUnblockingRequested() {
		if (getSelection() == null) return;
		try {
			controller(getSelection().info()).unblock(getSelection().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	private void onRemovalRequested() {
		if (getSelection() == null) return;
		final IslandAppInfo app = getSelection().info();
		Analytics.$().event("action_uninstall").with("package", app.packageName).with("system", app.isSystem() ? 1 : 0).send();
		if (app.isSystem()) {
			Dialogs.buildAlert(mActivity, 0, R.string.prompt_disable_sys_app_as_removal).withCancelButton()
					.setPositiveButton(R.string.dialog_button_continue, (d, w) -> launchSettingsAppInfoActivity(app)).show();
		} else try {
			final IIslandManager controller = controller(app);
			if (app.isHidden()) controller.unfreezeApp(app.packageName);	// Unfreeze it first, otherwise we cannot receive the package removal event.
			controller.removeClone(app.packageName);
		} catch (final RemoteException ignored) {
			final LauncherApps launcher_apps = (LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
			launcher_apps.startAppDetailsActivity(new ComponentName(app.packageName, ""), GlobalStatus.profile, null, null);
			Toast.makeText(mActivity, "Click \"Uninstall\" to remove the clone.", Toast.LENGTH_LONG).show();
		}
	}

	/** Possible 10s delay before the change broadcast could be received (due to Android issue 225880), so we force a refresh immediately. */
	private void refreshAppStateAsSysBugWorkaround(final String pkg) {
		IslandAppListProvider.getInstance(mActivity).refreshPackage(pkg, GlobalStatus.profile, false);
	}

	private void cloneApp(final IslandAppInfo app) {
		final int check_result;
		final String pkg = app.packageName;
		if (Users.isProfile(app.user)) {
			mActivity.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null)));
			Analytics.$().event("action_install_outside").with("package", pkg).send();
			return;
		}

		final IslandAppInfo app_in_profile = IslandAppListProvider.getInstance(mActivity).get(app.packageName, GlobalStatus.profile);
		if (app_in_profile != null && app_in_profile.isInstalled() && ! app_in_profile.enabled) {
			launchSettingsAppInfoActivity(app_in_profile);
			return;
		}

		try {
			check_result = mProfileController.cloneApp(pkg, false);
		} catch (final RemoteException ignored) { return; }		// FIXME: Error message
		switch (check_result) {
		case IslandManager.CLONE_RESULT_NOT_FOUND:    			// FIXME: Error message
			Toast.makeText(mActivity, R.string.toast_internal_error, Toast.LENGTH_SHORT).show();
		case IslandManager.CLONE_RESULT_ALREADY_CLONED:
			Toast.makeText(mActivity, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show();
			return;
		case IslandManager.CLONE_RESULT_NO_SYS_MARKET:
			Dialogs.buildAlert(mActivity, 0, R.string.dialog_clone_incapable_explanation)
					.setNeutralButton(R.string.dialog_button_learn_more, (d, w) -> WebContent.view(mActivity, Config.URL_CANNOT_CLONE_EXPLAINED.get()))
					.setPositiveButton(android.R.string.cancel, null).show();
			return;
		case IslandManager.CLONE_RESULT_OK_SYS_APP:
			Analytics.$().event("clone_sys").with("package", pkg).send();
			doCloneApp(pkg);
			break;
		case IslandManager.CLONE_RESULT_OK_INSTALL:
			Analytics.$().event("clone_install").with("package", pkg).send();
			showExplanationBeforeCloning("clone-via-install-explained", R.string.dialog_clone_via_install_explanation, pkg);
			break;
		case IslandManager.CLONE_RESULT_OK_GOOGLE_PLAY:
			Analytics.$().event("clone_app").with("package", pkg).send();
			showExplanationBeforeCloning("clone-via-google-play-explained", R.string.dialog_clone_via_google_play_explanation, pkg);
			break;
		case IslandManager.CLONE_RESULT_UNKNOWN_SYS_MARKET:
			final Intent market_intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			final ActivityInfo market_info = market_intent.resolveActivityInfo(mActivity.getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY);
			if (market_info != null && (market_info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				Analytics.$().setProperty("sys_market", market_info.packageName);
			showExplanationBeforeCloning("clone-via-sys-market-explained", R.string.dialog_clone_via_sys_market_explanation, pkg);
			break;
		}
	}

	private void doCloneApp(final String pkg) {
		final int result; try {
			result = mProfileController.cloneApp(pkg, true);
		} catch (final RemoteException ignored) { return; }	// FIXME: Error message
		switch (result) {
		case IslandManager.CLONE_RESULT_OK_SYS_APP:
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

	private void showExplanationBeforeCloning(final String mark, final @StringRes int explanation, final String pkg) {
		if (! Scopes.app(mActivity).isMarked(mark)) {
			Dialogs.buildAlert(mActivity, 0, explanation).setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
				Scopes.app(mActivity).mark(mark);
				doCloneApp(pkg);
			}).show();
		} else doCloneApp(pkg);
	}

	public final void onItemClick(final View view) {
		final AppEntryBinding binding = DataBindingUtil.findBinding(view);
		final AppViewModel clicked = binding.getApp();
		setSelection(clicked != getSelection() ? clicked : null);	// Click the selected one to deselect
	}

	public final void onBottomSheetClick(final View view) {
		final BottomSheetBehavior bottom_sheet = BottomSheetBehavior.from(view);
		bottom_sheet.setState(BottomSheetBehavior.STATE_EXPANDED);
	}

	private IIslandManager controller(final IslandAppInfo app) {
		return Users.isOwner(app.user) ? mOwnerController : mProfileController;
	}

	public List<Filter.Entry> getFilterPrimaryOptions() {		// Referenced by <Spinner> in layout
		return mFilterPrimaryOptions;
	}

	/* Parcelable */

	private AppListViewModel(final Parcel in) {
		super(AppViewModel.class);
		mFilterPrimaryChoice = in.readByte();
		mFilterIncludeSystemApps = in.readByte() != 0;
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeByte((byte) mFilterPrimaryChoice);
		dest.writeByte((byte) (mFilterIncludeSystemApps ? 1 : 0));
	}

	@Override public int describeContents() { return 0; }
	public static final Creator<AppListViewModel> CREATOR = new Creator<AppListViewModel>() {
		@Override public AppListViewModel createFromParcel(final Parcel in) { return new AppListViewModel(in); }
		@Override public AppListViewModel[] newArray(final int size) { return new AppListViewModel[size]; }
	};


	public final BottomSheetBehavior.BottomSheetCallback bottom_sheet_callback = new BottomSheetBehavior.BottomSheetCallback() {

		@Override public void onStateChanged(@NonNull final View bottom_sheet, final int new_state) {
			if (new_state == BottomSheetBehavior.STATE_HIDDEN) clearSelection();
		}

		@Override public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {}
	};

	public final ItemBinder<AppViewModel> item_binder = new ItemBinder<AppViewModel>() {

		@Override public int getLayoutRes(final AppViewModel model) {
			return R.layout.app_entry;
		}

		@Override public void onBind(final ViewDataBinding container, final AppViewModel model, final ViewDataBinding item) {
			item.setVariable(BR.app, model);
			item.setVariable(BR.apps, ((AppListBinding) container).getApps());
		}
	};

	/* Attachable fields */
	private Activity mActivity;
	private IIslandManager mOwnerController;
	private Menu mActions;
	private List<Filter.Entry> mFilterPrimaryOptions;
	private Predicate<IslandAppInfo> mFilterExtras;		// All other filters to apply always
	/* Parcelable fields */
	private int mFilterPrimaryChoice;
	private boolean mFilterIncludeSystemApps;
	/* Transient fields */
	public transient IIslandManager mProfileController;
	private transient Predicate<IslandAppInfo> mActiveFilters;		// The active composite filters

	private static final String TAG = "Island.Apps";
}
