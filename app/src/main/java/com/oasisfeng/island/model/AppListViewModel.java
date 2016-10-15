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
import android.databinding.Bindable;
import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.databinding.ViewDataBinding;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandle;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomSheetBehavior;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.common.app.BaseAppListViewModel;
import com.oasisfeng.island.BR;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.R;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.databinding.AppEntryBinding;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.greenify.GreenifyClient;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;
import com.oasisfeng.island.util.Users;

/**
 * View model for apps
 *
 * Created by Oasis on 2015/7/7.
 */
@SuppressWarnings("unused")
public class AppListViewModel extends BaseAppListViewModel<AppViewModel> {

	private enum FabAction { None, Clone, Lock, Unlock, Enable }

	public boolean include_sys_apps;
	public transient @Nullable IIslandManager mController;

	public AppListViewModel(final Activity activity) {
		super(AppViewModel.class);
		mActivity = activity;
		addOnPropertyChangedCallback(new OnPropertyChangedCallback() { @Override public void onPropertyChanged(final Observable sender, final int property) {
			if (property == BR.selection) updateFab();
		}});
	}

	private void updateFab() {
		if (getSelection() != null) switch (getSelection().state) {
		case Alive: setFabAction(FabAction.Lock); break;
		case Frozen: setFabAction(FabAction.Unlock); break;
		case Disabled: setFabAction(FabAction.Enable); break;
		case NotCloned: setFabAction(FabAction.Clone); break;
		default: setFabAction(FabAction.None); break;
		} else setFabAction(FabAction.None);
	}

	public final void onItemLaunchIconClick(final View view) {
		if (getSelection() == null) return;
		final String pkg = getSelection().info.packageName;
		Analytics.$().event("action_launch").with("package", pkg).send();
		try {
			mController.launchApp(pkg);
		} catch (final RemoteException ignored) {}
	}

	public void onShortcutRequested(final View v) {
		if (getSelection() == null) return;
		final String pkg = getSelection().info().packageName;
		Analytics.$().event("action_create_shortcut").with("package", pkg).send();
		if (AppLaunchShortcut.createOnLauncher(mActivity, pkg, Users.isOwner(getSelection().info().user))) {
			Toast.makeText(mActivity, R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();
		} else Toast.makeText(mActivity, R.string.toast_shortcut_failed, Toast.LENGTH_SHORT).show();
	}

	public void onGreenifyRequested(final View v) {
		if (getSelection() == null) return;
		Analytics.$().event("action_greenify").with("package", getSelection().info().packageName).send();

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
					greenify(getSelection().info().packageName, getSelection().info().user);
				} else GreenifyClient.openInAppMarket(mActivity);
			}).show();
		} else greenify(getSelection().info.packageName, getSelection().info().user);
	}

	private void greenify(final String pkg, final UserHandle user) {
		if (! GreenifyClient.greenify(mActivity, pkg, user))
			Toast.makeText(mActivity, R.string.toast_greenify_failed, Toast.LENGTH_LONG).show();
	}

	public void onBlockingRequested(final View v) {
		if (getSelection() == null) return;
		try {
			mController.block(getSelection().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	public void onUnblockingRequested(final View v) {
		if (getSelection() == null) return;
		try {
			mController.unblock(getSelection().info.packageName);
		} catch (final RemoteException ignored) {}
	}

	public void onRemovalRequested(final View v) {
		if (getSelection() == null) return;
		final String pkg = getSelection().info.packageName;
		Analytics.$().event("action_uninstall").with("package", pkg).send();
		try {
			mController.removeClone(pkg);
		} catch (final RemoteException ignored) {
			final LauncherApps launcher_apps = (LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
			launcher_apps.startAppDetailsActivity(new ComponentName(pkg, ""), GlobalStatus.profile, null, null);
			Toast.makeText(mActivity, "Click \"Uninstall\" to remove the clone.", Toast.LENGTH_LONG).show();
		}
	}

	public void onOwnerInstallationRequested(final View v) {
		if (getSelection() == null) return;
//		mController.installForOwner(getSelection().info.packageName);
	}

	public final void onFabClick(final View view) {
		final AppViewModel selection = getSelection();
		if (selection == null) return;
		final String pkg = selection.info.packageName;
		switch (fab_action) {
		case Clone:
			cloneApp(pkg);
			// Do not clear selection, for quick launch with one more click
			break;
		case Lock:
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
				final boolean frozen = mController.freezeApp(pkg, "manual");
				if (! frozen) Toast.makeText(mActivity, "Failed to freeze", Toast.LENGTH_LONG).show();
			} catch (final RemoteException ignored) {}
			break;
		case Unlock:
			Analytics.$().event("action_defreeze").with("package", pkg).send();
			try {
				mController.defreezeApp(pkg);
			} catch (final RemoteException ignored) {}
			// Do not clear selection, for quick launch with one more click
			break;
		case Enable:
			final LauncherApps launcher_apps = (LauncherApps) mActivity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
			launcher_apps.startAppDetailsActivity(new ComponentName(pkg, ""), selection.info().user, null, null);
			break;
		}
	}

	private void cloneApp(final String pkg) {
		final boolean allowed;
		final int check_result;
		try {
			check_result = mController.cloneApp(pkg, false);
		} catch (final RemoteException ignored) { return; }		// FIXME: Error message
		switch (check_result) {
		case IslandManager.CLONE_RESULT_NOT_FOUND:    			// FIXME: Error message
			Toast.makeText(mActivity, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show();
		case IslandManager.CLONE_RESULT_ALREADY_CLONED:
			Toast.makeText(mActivity, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show();
			return;
		case IslandManager.CLONE_RESULT_NO_SYS_MARKET:
			new AlertDialog.Builder(mActivity).setMessage(R.string.dialog_clone_incapable_explanation)
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
			result = mController.cloneApp(pkg, true);
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
			new AlertDialog.Builder(mActivity).setMessage(R.string.dialog_clone_via_install_explanation).setPositiveButton(R.string.dialog_button_continue, (d, w) -> {
				Scopes.app(mActivity).mark(mark);
				doCloneApp(pkg);
			}).show();
		} else doCloneApp(pkg);
	}

	private void setFabAction(final FabAction action) {
		if (action == fab_action) return;
		fab_action = action;
		notifyPropertyChanged(BR.fabImage);
		notifyPropertyChanged(BR.fabBgColor);
	}

	private FabAction fab_action = FabAction.None;

	@Bindable public @DrawableRes int getFabImage() {
		switch (fab_action) {
		case Clone: return R.drawable.ic_copy_24dp;
		case Lock: return R.drawable.ic_lock_24dp;
		case Unlock: return R.drawable.ic_unlock_24dp;
		case Enable: return R.drawable.ic_enable_24dp;
		default: return 0; }
	}

	@Bindable public @ColorRes int getFabBgColor() {
		switch (fab_action) {
		case Lock: return R.color.state_frozen;
		case Clone: case Unlock: case Enable: return R.color.state_alive;
		default: return 0; }
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

	public void setIslandManager(final IIslandManager manager) { mController = manager; }

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

	private final Activity mActivity;

	private static final String TAG = "Island.VM";
}
