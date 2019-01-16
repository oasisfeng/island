package com.oasisfeng.common.app;

import android.util.Log;

import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.island.model.AppViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.databinding.ObservableList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * View-model of basic entry-selectable (single-choice) app-list.
 *
 * Created by Oasis on 2016/6/24.
 */
public class BaseAppListViewModel<T extends AppViewModel> extends ViewModel {

	// TODO: Rebuild the whole AbstractAppListViewModel to keep immutability?
	protected void replaceApps(final List<T> apps) {
		mApps.clear();
		mAppsByPackage.clear();
		for (final T app : apps)
			mAppsByPackage.put(app.info.packageName, app);
		mApps.addAll(apps);
	}

	protected T putApp(final String pkg, final T app) {
		final T old_app_vm = mAppsByPackage.put(pkg, app);
		if (old_app_vm != null) {
			Log.d(TAG, "Update in place: " + pkg);
			final int index = mApps.indexOf(old_app_vm);
			mApps.updateItemAt(index, app);
			if (mSelection.getValue() == old_app_vm) setSelection(app);	// Keep the selection unchanged
		} else {
			Log.d(TAG, "Put: " + pkg);
			mApps.add(app);
		}
		return app;
	}

	protected T getApp(final String pkg) {
		return mAppsByPackage.get(pkg);
	}

	protected AppViewModel getAppAt(final int index) {
		return mApps.get(index);
	}

	protected int indexOf(final T app) {
		return mApps.indexOf(app);
	}

	/** STOP: This method only serves the generated binding class, and should never be called directly. */
	@Deprecated public ObservableList<T> getItems() {
		return mApps;
	}

	protected void removeApp(final String pkg) {
		if (pkg == null) return;
		final T app = mAppsByPackage.remove(pkg);
		if (app == null) return;
		Log.d(TAG, "Remove: " + pkg);
		mApps.remove(app);
		if (mSelection.getValue() == app) setSelection(null);
	}

	protected boolean contains(final String pkg) { return mAppsByPackage.containsKey(pkg); }
	protected int size() { return mApps.size(); }

	/* Selection related */

	public void clearSelection() {
		setSelection(null);
	}

	protected void setSelection(final T selection) {
		if (mSelection.getValue() == selection) return;
		if (mSelection.getValue() != null) mSelection.getValue().selected.setValue(false);
		mSelection.setValue(selection);
		if (selection != null) selection.selected.setValue(true);
	}

	protected BaseAppListViewModel(final Class<T> clazz) {
		mApps = new ObservableSortedList<>(clazz);
	}

	private static final String TAG = "Island.Apps.Base";

	private final ObservableSortedList<T> mApps;
	private final Map<String, T> mAppsByPackage = new HashMap<>();	// Enforced constraint: apps from different users must not be shown at the same time.
	public final MutableLiveData<T> mSelection = new MutableLiveData<>();
}
