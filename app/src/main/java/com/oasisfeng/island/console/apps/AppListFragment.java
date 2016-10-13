package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ServiceConnection;
import android.databinding.Observable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.oasisfeng.android.service.Services;
import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.R;
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppViewModel;
import com.oasisfeng.island.shuttle.ServiceShuttle.ShuttleServiceConnection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java8.util.function.Predicate;
import java8.util.function.Predicates;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static com.oasisfeng.island.data.IslandAppListProvider.NON_SYSTEM;

/** The main UI - App list */
public class AppListFragment extends Fragment {

	private static final String KStateKeyRecyclerView = "apps.recycler.layout";

	private Predicate<IslandAppInfo> mExcludeSelf;
	private static final Predicate<IslandAppInfo> CLONED = app -> app.user.hashCode() != 0 && app.isInstalled();
	private static final Predicate<IslandAppInfo> CLONEABLE = app -> app.user.hashCode() == 0;		// FIXME: Exclude already cloned

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		final Activity activity = getActivity();

		mIslandManager = new IslandManager(activity);
		mViewModel = new AppListViewModel(activity);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);
		mExcludeSelf = IslandAppListProvider.excludeSelf(activity);

		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
	}

	@Override public void onStart() {
		super.onStart();
		if (! Services.bind(getActivity(), IIslandManager.class, mServiceConnection))
			Toast.makeText(getActivity(), "Error opening Island", Toast.LENGTH_LONG).show();
	}

	@Override public void onStop() {
		try {
			getActivity().unbindService(mServiceConnection);
		} catch (final RuntimeException ignored) {}
		mBinding.getApps().clearSelection();
		super.onStop();
	}

	@Override public void onDestroy() {
		IslandAppListProvider.getInstance(getActivity()).unregisterObserver(mAppChangeObserver);
		mViewModel.removeOnPropertyChangedCallback(onPropertyChangedCallback);
		super.onDestroy();
	}

	// Use ShuttleServiceConnection to connect to remote service in profile via ServiceShuttle (see also MainActivity.bindService)
	private final ServiceConnection mServiceConnection = new ShuttleServiceConnection() {
		@Override public void onServiceConnected(final IBinder service) {
			mViewModel.setIslandManager(IIslandManager.Stub.asInterface(service));
			Log.d(TAG, "Service connected");
		}

		@Override public void onServiceDisconnected() {
			mViewModel.setIslandManager(null);
		}
	};

	@Override public void onResume() {
		super.onResume();
		mIsDeviceOwner = mIslandManager.isDeviceOwner();
	}

	AppListProvider.PackageChangeObserver<IslandAppInfo> mAppChangeObserver = new AppListProvider.PackageChangeObserver<IslandAppInfo>() {

		@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
			final Predicate<IslandAppInfo> filters = activeFilters();
			for (final IslandAppInfo app : apps) {
				if (filters.test(app)) mViewModel.putApp(app.packageName, new AppViewModel(app));
				else mViewModel.removeApp(app.packageName);
			}
// TODO
//			Snackbars.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app.getLabel()),
//					Snackbars.withAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(activity, pkg))).show();
			invalidateOptionsMenu();
		}

		@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {
			final Predicate<IslandAppInfo> filters = activeFilters();
			for (final IslandAppInfo app : apps)
				if (filters.test(app)) mViewModel.removeApp(app.packageName);
			invalidateOptionsMenu();
		}
	};

	private final Observable.OnPropertyChangedCallback onPropertyChangedCallback = new Observable.OnPropertyChangedCallback() {
		@Override public void onPropertyChanged(final Observable observable, final int var) {
			if (var == com.oasisfeng.island.BR.selection) invalidateOptionsMenu();
		}
	};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mBinding.appList.setLayoutManager(new LinearLayoutManager(getActivity()));
		getActivity().setActionBar(mBinding.appbar);
		mBinding.filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
				onListFilterChanged(position);
			}
			@Override public void onNothingSelected(final AdapterView<?> parent) {}
		});
		// Work-around a bug in Android N DP4.
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) mBinding.appbar.inflateMenu(R.menu.main_actions);
		return mBinding.getRoot();
	}

	private void onListFilterChanged(final int index) {
		switch (index) {
		case 0: mAppsSubsetFilter = Predicates.and(mExcludeSelf, CLONED); break;
		case 1: mAppsSubsetFilter = Predicates.and(mExcludeSelf, CLONEABLE); break;
		default: throw new IllegalStateException();
		}
		mViewModel.clearSelection();
		rebuildAppViewModels();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		menu.findItem(R.id.menu_show_system).setChecked(mShowSystemApps);
		menu.findItem(R.id.menu_destroy).setVisible(! mIsDeviceOwner);
		menu.findItem(R.id.menu_deactivate).setVisible(mIsDeviceOwner);
		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_show_system:
			item.setChecked(mViewModel.include_sys_apps = mShowSystemApps = ! item.isChecked());	// Toggle the checked state
			mViewModel.clearSelection();
			rebuildAppViewModels();
			return true;
		case R.id.menu_destroy:
		case R.id.menu_deactivate:
			destroy();
			return true;
		case R.id.menu_test:
			TempDebug.run(getActivity());
		}
		return super.onOptionsItemSelected(item);
	}

	@Override public void onSaveInstanceState(final Bundle out_state) {
		super.onSaveInstanceState(out_state);
		out_state.putParcelable(KStateKeyRecyclerView, mBinding.appList.getLayoutManager().onSaveInstanceState());
	}

	@Override public void onViewStateRestored(final Bundle saved_state) {
		super.onViewStateRestored(saved_state);
		if (saved_state != null)
			mBinding.appList.getLayoutManager().onRestoreInstanceState(saved_state.getParcelable(KStateKeyRecyclerView));
	}

	private void rebuildAppViewModels() {
		final List<AppViewModel> apps = IslandAppListProvider.getInstance(getActivity()).installedApps()
				.filter(activeFilters()).map(AppViewModel::new).collect(Collectors.toList());
		mViewModel.replaceApps(apps);
	}

	private Predicate<IslandAppInfo> activeFilters() {
		return mShowSystemApps ? mAppsSubsetFilter : Predicates.and(mAppsSubsetFilter, NON_SYSTEM);
	}

	public void destroy() {
		final Activity activity = getActivity();
		final Map<Boolean, List<IslandAppInfo>> partitions = IslandAppListProvider.getInstance(activity).installedApps()
				.filter(IslandAppListProvider.NON_SYSTEM).collect(Collectors.partitioningBy(app -> app.user.hashCode() == 0 && app.isInstalled()));
		final Set<String> owner_installed = StreamSupport.stream(partitions.get(Boolean.TRUE)).map(app -> app.packageName).collect(Collectors.toSet());
		final List<String> exclusive_clones = StreamSupport.stream(partitions.get(Boolean.FALSE))	// Island installed
				.filter(app -> ! owner_installed.contains(app.packageName)).map(AppInfo::getLabel).collect(Collectors.toList());

		if (mIslandManager.isDeviceOwner()) {
			new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_deactivate_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_deactivate, (d, w) -> mIslandManager.deactivateDeviceOwner()).show();
		} else if (IslandManager.isProfileOwner(activity)) {
			new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
					.setMessage(R.string.dialog_destroy_message)
					.setPositiveButton(android.R.string.no, null)
					.setNeutralButton(R.string.dialog_button_destroy, (d, w) -> {
						if (exclusive_clones.isEmpty()) {
							destroyProfile();
							return;
						}
						final String names = Joiner.on('\n').skipNulls().join(Iterables.limit(exclusive_clones, MAX_DESTROYING_APPS_LIST));
						final String names_ellipsis = exclusive_clones.size() <= MAX_DESTROYING_APPS_LIST ? names : names + "…\n";
						new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_warning)
								.setMessage(activity.getString(R.string.dialog_destroy_exclusives_message, exclusive_clones.size(), names_ellipsis))
								.setNeutralButton(R.string.dialog_button_destroy, (dd, ww) -> destroyProfile())
								.setPositiveButton(android.R.string.no, null).show();
					}).show();
		} else {
			new AlertDialog.Builder(activity).setMessage(R.string.dialog_cannot_destroy_message)
					.setNegativeButton(android.R.string.ok, null).show();
			Analytics.$().event("cannot_destroy").send();
		}
	}
	private static final int MAX_DESTROYING_APPS_LIST = 8;

	private void destroyProfile() {
		final Activity activity = getActivity();
		final IIslandManager controller = mViewModel.mController;
		if (controller != null) try {
			controller.destroyProfile();
			activity.finish();
			return;
		} catch (final RemoteException ignored) {}

		Toast.makeText(activity, "Failed", Toast.LENGTH_LONG).show();
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private IslandManager mIslandManager;
	private AppListViewModel mViewModel;
	private AppListBinding mBinding;
	private Predicate<IslandAppInfo> mAppsSubsetFilter;
	private boolean mShowSystemApps;
	private boolean mIsDeviceOwner;
	private static final String TAG = "Island.AppsUI";
}
