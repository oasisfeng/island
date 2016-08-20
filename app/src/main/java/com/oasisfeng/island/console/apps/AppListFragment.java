package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.app.Fragment;
import android.databinding.Observable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.oasisfeng.common.app.AppInfo;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.R;
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppViewModel;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.provisioning.IslandProvisioning;

import java.util.List;
import java.util.Map;

import java8.util.function.Predicate;
import java8.util.function.Predicates;
import java8.util.stream.Collectors;
import java8.util.stream.RefStreams;

import static com.oasisfeng.island.data.IslandAppListProvider.NON_SYSTEM;

/** The main UI - App list */
public class AppListFragment extends Fragment {

	private static final String KStateKeyRecyclerView = "apps.recycler.layout";

	private static final Predicate<IslandAppInfo> CLONED = IslandAppInfo::isInstalledInUser;
	private static final Predicate<IslandAppInfo> CLONEABLE = Predicates.negate(IslandAppInfo::isInstalledInUser);

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		final Activity activity = getActivity();

		mIslandManager = new IslandManager(activity);
		mViewModel = new AppListViewModel(mIslandManager);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);

		IslandAppListProvider.getInstance(activity).registerObserver(this::onPackageEvent);

		new IslandProvisioning(activity, mIslandManager).startProfileOwnerProvisioningIfNeeded();

		rebuildAppViewModels();
	}

	@Override public void onDestroy() {
		IslandAppListProvider.getInstance(getActivity()).unregisterObserver(this::onPackageEvent);
		mViewModel.removeOnPropertyChangedCallback(onPropertyChangedCallback);
		super.onDestroy();
	}

	private void onPackageEvent(final String[] pkgs) {
		final Activity activity = getActivity();
		if (activity == null) return;
		final Map<String, IslandAppInfo> apps = IslandAppListProvider.getInstance(activity).map();

		final Predicate<IslandAppInfo> filters = activeFilters();
		RefStreams.of(pkgs).map(apps::get).filter(app -> app != null).forEach(app -> {
			if (filters.test(app)) mViewModel.putApp(app.packageName, new AppViewModel(app));
			else mViewModel.removeApp(app.packageName);
		});

// TODO
//		Snackbars.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app.getLabel()),
//				Snackbars.withAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(activity, pkg))).show();

		invalidateOptionsMenu();
	}

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
		case 0: mAppsSubsetFilter = CLONED; break;
		case 1: mAppsSubsetFilter = CLONEABLE; break;
		default: throw new IllegalStateException();
		}
		rebuildAppViewModels();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		menu.findItem(R.id.menu_show_system).setChecked(mShowSystemApps);
		menu.findItem(R.id.menu_destroy).setVisible(! GlobalStatus.running_in_owner);
		menu.findItem(R.id.menu_deactivate).setVisible(GlobalStatus.running_in_owner);
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
			final List<String> exclusive_clones = IslandAppListProvider.getInstance(getActivity()).installedApps()
					.filter(IslandAppListProvider.NON_SYSTEM).filter(app -> ! app.checkInstalledInOwner())
					.map(AppInfo::getLabel).collect(Collectors.toList());
			mIslandManager.destroy(exclusive_clones);
			return true;
		case R.id.menu_test:
			TempDebug.run(getActivity());
		}
		return super.onOptionsItemSelected(item);
	}

	@Override public void onStop() {
		mBinding.getApps().clearSelection();
		super.onStop();
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

//	private Map<String, AppInfo> populateApps() {
//		final Context context = getActivity();
//		return AppList.installed(context).filter(AppList.excludeSelf(context)).collect(Collectors.toMap());
//	}
//
//	private Map<String, AppInfo> populateApps(final boolean all) {
//		final Context context = getActivity();
//		final AppList all_apps = AppList.all(context).excludeSelf();
//		final FluentIterable<AppInfo> apps = all ? all_apps.build()
//				: all_apps.build().filter(INSTALLED_IN_USER).filter(or(NON_SYSTEM, and(NON_CRITICAL_SYSTEM, all_apps.LAUNCHABLE)));
//		// TODO: Also include system apps with running services (even if no launcher activities)
//		final ImmutableList<AppInfo> ordered_apps = apps.toSortedList(AppList.CLONED_FIRST);
//
//		final Map<String, ApplicationInfo> app_vm_by_pkg = new LinkedHashMap<>();	// LinkedHashMap to preserve the order
//		for (final ApplicationInfo app : ordered_apps)
//			app_vm_by_pkg.put(app.packageName, app);
//		return app_vm_by_pkg;
//	}

	private void rebuildAppViewModels() {
		final List<AppViewModel> apps = IslandAppListProvider.getInstance(getActivity()).installedApps()
				.filter(IslandAppListProvider.excludeSelf(getActivity()))
				.filter(activeFilters()).map(AppViewModel::new).collect(Collectors.toList());
		mViewModel.replaceApps(apps);
	}

	private Predicate<IslandAppInfo> activeFilters() {
		return mShowSystemApps ? mAppsSubsetFilter : Predicates.and(mAppsSubsetFilter, NON_SYSTEM);
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private IslandManager mIslandManager;
	private AppListViewModel mViewModel;
	private AppListBinding mBinding;
	private Predicate<IslandAppInfo> mAppsSubsetFilter = CLONED;
	private boolean mShowSystemApps;
}
