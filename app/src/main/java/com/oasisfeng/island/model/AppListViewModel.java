package com.oasisfeng.island.model;

import android.databinding.Bindable;
import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.databinding.ViewDataBinding;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.view.View;

import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.common.app.BaseAppListViewModel;
import com.oasisfeng.island.BR;
import com.oasisfeng.island.R;
import com.oasisfeng.island.databinding.AppEntryBinding;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.model.AppViewModel.State;

/**
 * View model for apps
 *
 * Created by Oasis on 2015/7/7.
 */
@SuppressWarnings("unused")
public class AppListViewModel extends BaseAppListViewModel<AppViewModel> {

	public interface Controller {
		void cloneApp(String pkg);
		/** @return whether the package is frozen, or true if not found */
		boolean freezeApp(String pkg, String reason);
		/** @return whether the package is unfrozen, or false if not found */
		boolean defreezeApp(String pkg);
		void enableApp(String pkg);
		void launchApp(String pkg);
		void createShortcut(String pkg);
		void greenify(String pkg);
		boolean block(String pkg);
		boolean unblock(String pkg);
		void removeClone(String pkg);
		void installForOwner(String pkg);
	}

	private enum FabAction { None, Clone, Lock, Unlock, Enable }

	public boolean include_sys_apps;
	private final transient Controller mController;

	public AppListViewModel(final Controller controller) {
		super(AppViewModel.class);
		mController = controller;
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
		mController.launchApp(getSelection().info.packageName);
	}

	public void onShortcutRequested(final View v) {
		if (getSelection() == null) return;
		mController.createShortcut(getSelection().info.packageName);
	}

	public void onGreenifyRequested(final View v) {
		if (getSelection() == null) return;
		mController.greenify(getSelection().info.packageName);
	}

	public void onBlockingRequested(final View v) {
		if (getSelection() == null) return;
		mController.block(getSelection().info.packageName);
	}

	public void onUnblockingRequested(final View v) {
		if (getSelection() == null) return;
		mController.unblock(getSelection().info.packageName);
	}

	public void onRemovalRequested(final View v) {
		if (getSelection() == null) return;
		mController.removeClone(getSelection().info.packageName);
	}

	public void onOwnerInstallationRequested(final View v) {
		if (getSelection() == null) return;
		mController.installForOwner(getSelection().info.packageName);
	}

	public final void onFabClick(final View view) {
		final AppViewModel selection = getSelection();
		if (selection == null) return;
		final String pkg = selection.info.packageName;
		switch (fab_action) {
		case Clone:
			mController.cloneApp(pkg);
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
			mController.freezeApp(pkg, "manual");
			break;
		case Unlock:
			mController.defreezeApp(pkg);
			// Do not clear selection, for quick launch with one more click
			break;
		case Enable:
			mController.enableApp(pkg);
			break;
		}
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
}
