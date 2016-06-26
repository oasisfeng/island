package com.oasisfeng.island.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableList;
import android.support.annotation.Nullable;

import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.island.BR;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for view models in app-list style.
 *
 * Created by Oasis on 2016/6/24.
 */
public class AbstractAppListViewModel extends BaseObservable {

	private final ObservableSortedList<AppViewModel> apps = new ObservableSortedList<>(AppViewModel.class);
	private final Map<String, AppViewModel> apps_by_pkg = new HashMap<>();
	private transient AppViewModel selection;

	void putApp(final AppViewModel app) {
		apps_by_pkg.put(app.pkg, app);
		apps.add(app);
	}

	public @Nullable AppViewModel getApp(final String pkg) {
		return apps_by_pkg.get(pkg);
	}

	AppViewModel getAppAt(final int index) {
		return apps.get(index);
	}

	int indexOf(final AppViewModel app) {
		return apps.indexOf(app);
	}

	Collection<AppViewModel> allApps() {
		return apps_by_pkg.values();
	}

	@Deprecated // For generated binding class only, should never be called.
	public ObservableList<AppViewModel> getItems() {
		return apps;
	}

	void removeApp(final String pkg) {
		if (pkg == null) return;
		final AppViewModel app = apps_by_pkg.remove(pkg);
		if (app == null) return;
		apps.remove(app);
	}

	public void removeAllApps() {
		apps_by_pkg.clear();
		apps.clear();
	}

	public int size() { return apps.size(); }
	public boolean isEmpty() { return apps_by_pkg.isEmpty(); }

	void updateAppAt(final int index, final AppViewModel app) {
		apps.updateItemAt(index, app);
	}

	/* Selection related */

	@Bindable public @Nullable AppViewModel getSelection() { return selection; }

	public void clearSelection() {
		setSelection(null);
	}

	protected void setSelection(final AppViewModel selection) {
		if (this.selection == selection) return;
		if (this.selection != null) this.selection.selected.set(false);
		this.selection = selection;
		if (selection != null) selection.selected.set(true);
		notifyPropertyChanged(BR.selection);
	}
}
