package com.oasisfeng.island.model;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.view.View;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.island.BR;
import com.oasisfeng.island.R;
import com.oasisfeng.island.databinding.AppEntryBinding;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.model.AppViewModel.State;

import java.util.HashMap;
import java.util.Map;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

/**
 * View model for apps
 *
 * Created by Oasis on 2015/7/7.
 */
@SuppressWarnings("unused")
public class AppListViewModel extends BaseObservable {

	public interface Controller {
		@Nullable ApplicationInfo getAppInfo(String pkg);
		State getAppState(ApplicationInfo pkg);
		void cloneApp(String pkg);
		void freezeApp(String pkg);
		void defreezeApp(String pkg);
		void enableApp(String pkg);
		void launchApp(String pkg);
		void createShortcut(String pkg);
		void removeClone(String pkg);
		void installForOwner(String pkg);
		CharSequence readAppName(String pkg) throws PackageManager.NameNotFoundException;
		boolean isCloneExclusive(String pkg);
	}

	enum FabAction { None, Clone, Lock, Unlock, Enable }

	public boolean include_sys_apps;
	private final ObservableSortedList<AppViewModel> apps = new ObservableSortedList<>(AppViewModel.class);
	private final Map<String, AppViewModel> apps_by_pkg = new HashMap<>();
	private final transient Controller mController;
	private transient AppViewModel selection;

	@Bindable public @Nullable AppViewModel getSelection() { return selection; }

	public void clearSelection() {
		setSelection(null);
	}

	private void setSelection(final AppViewModel selection) {
		if (this.selection == selection) return;
		if (this.selection != null) this.selection.selected.set(false);
		this.selection = selection;
		if (selection != null) selection.selected.set(true);
		updateFab();
		notifyPropertyChanged(BR.selection);
	}

	public AppListViewModel(final Controller controller) {
		mController = controller;
	}

	private void updateFab() {
		if (selection != null) switch (selection.getState()) {
		case Alive: setFabAction(FabAction.Lock); break;
		case Frozen: setFabAction(FabAction.Unlock); break;
		case Disabled: setFabAction(FabAction.Enable); break;
		case NotCloned: setFabAction(FabAction.Clone); break;
		default: setFabAction(FabAction.None); break;
		} else setFabAction(FabAction.None);
	}

	public void onShortcutRequested(final View v) {
		if (selection == null) return;
		mController.createShortcut(selection.pkg);
	}

	public void onRemovalRequested(final View v) {
		if (selection == null) return;
		mController.removeClone(selection.pkg);
	}

	public void onOwnerInstallationRequested(final View v) {
		if (selection == null) return;
		mController.installForOwner(selection.pkg);
	}

	/** This API is not provided in AppViewModel because we may need to update or remove the item in SortedList.
	 *  @return updated (or added) app view-model, null if not included or removed */
	public @Nullable AppViewModel updateApp(final String pkg) {
		final ApplicationInfo info = mController.getAppInfo(pkg);
		if (info == null) {
			removeApp(pkg);
			return null;
		}
		final State state = mController.getAppState(info);
		final AppViewModel app = getApp(pkg);
		if (app != null) {
			final int index = apps.indexOf(app);
			setAppState(app, state);
			app.exclusive.set(mController.isCloneExclusive(pkg));
			apps.updateItemAt(index, app);
			return app;
		} else if (include_sys_apps || (info.flags & FLAG_SYSTEM) == 0) try {
			return addApp(pkg, mController.readAppName(pkg), info.flags, state);
		} catch (final PackageManager.NameNotFoundException ignored) {}
		return null;
	}

	/**
	 * This API is not provided in AppViewModel for unique control point
	 */
	private void setAppState(final AppViewModel app, final State state) {
		final State last_state = app.getState();
		if (last_state == state) return;

		app.setState(state);
		updateFab();
	}

	public void removeAllApps() {
		apps_by_pkg.clear();
		apps.clear();
	}

	public AppViewModel addApp(final String pkg, final CharSequence name, final int flag) {
		final ApplicationInfo info = mController.getAppInfo(pkg);
		if (info == null) return null;
		return addApp(info, name, flag);
	}

	public AppViewModel addApp(final ApplicationInfo info, final CharSequence name, final int flag) {
		return addApp(info.packageName, name, flag, mController.getAppState(info));
	}

	private AppViewModel addApp(final String pkg, final CharSequence name, final int flag, final State state) {
		if (pkg == null) throw new IllegalArgumentException("pkg is null");
		final AppViewModel existent = apps_by_pkg.get(pkg);
		if (existent != null) return existent;
		final AppViewModel app = new AppViewModel(pkg, name, flag, mController.isCloneExclusive(pkg));
		setAppState(app, state);
		apps_by_pkg.put(pkg, app);
		apps.add(app);
		return app;
	}

	private void removeApp(final String pkg) {
		if (pkg == null) return;
		final AppViewModel app = apps_by_pkg.remove(pkg);
		if (app == null) return;
		apps.remove(app);
	}

	public @Nullable AppViewModel getApp(final String pkg) {
		return apps_by_pkg.get(pkg);
	}

	public boolean isEmpty() {
		return apps_by_pkg.isEmpty();
	}

	@Deprecated // For generated binding class only, should never be called.
	public ObservableList<AppViewModel> getItems() {
		return apps;
	}

	public ImmutableList<CharSequence> getNameOfExclusiveClones() {
		return FluentIterable.from(apps_by_pkg.values()).filter(app -> app.exclusive.get())
				.transform(app -> app.name).toList();
	}

	public final void onFabClick(final View view) {
		if (selection == null) return;
		final String pkg = selection.pkg;
		switch (fab_action) {
		case Clone:
			mController.cloneApp(pkg);
			// Do not clear selection, for quick launch with one more click
			break;
		case Lock:
			// Select the next alive app, or clear selection.
			final int next_index = apps.indexOf(selection) + 1;
			if (next_index >= apps.size()) clearSelection();
			else {
				final AppViewModel next = apps.get(next_index);
				if (next.getState() == State.Alive)
					setSelection(next);
				else clearSelection();
			}
			mController.freezeApp(pkg);
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
		setSelection(clicked != selection ? clicked : null);	// Click the selected one to deselect
	}

	public final void onItemLaunchIconClick(final View view) {
		if (selection == null) return;
		if (selection.getState() == State.Frozen) mController.defreezeApp(selection.pkg);
		mController.launchApp(selection.pkg);
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
