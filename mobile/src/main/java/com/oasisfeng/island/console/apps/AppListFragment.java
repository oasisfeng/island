package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.featured.FeaturedListViewModel;
import com.oasisfeng.island.guide.UserGuide;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.AppListBinding;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.MainViewModel;
import com.oasisfeng.island.settings.SettingsActivity;

import java.util.Collection;

import javax.annotation.ParametersAreNonnullByDefault;

/** The main UI - App list */
@ParametersAreNonnullByDefault
public class AppListFragment extends Fragment {

	@Override public void onCreate(final @Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);	// To keep view-model (by keeping the view-model provider)
		setHasOptionsMenu(true);
		final Activity activity = requireActivity();
		final ViewModelProvider provider = new ViewModelProvider(this);
		final AppListViewModel vm = mViewModel = provider.get("MainViewModel", MainViewModel.class);
		vm.mFeatured = provider.get(FeaturedListViewModel.class);
		mUserGuide = UserGuide.initializeIfNeeded(activity, this, vm);

		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
		vm.mFeatured.visible.observe(this, visible -> invalidateOptionsMenu());
		vm.mSelection.observe(this, s -> { invalidateOptionsMenu(); mViewModel.updateActions(mBinding.toolbar.getMenu()); });
		vm.getFilterIncludeHiddenSystemApps().observe(this, filter -> mViewModel.updateAppList());
		vm.getFilterText().observe(this, text -> mViewModel.updateAppList());
	}

	@Override public void onResume() {
		super.onResume();
		if (SystemClock.uptimeMillis() - mTimeLastPaused < 1_000) return;	// Avoid updating for brief pausing caused by cross-profile functionality.
		if (mViewModel.mFeatured.visible.getValue()) mViewModel.mFeatured.update(requireActivity());
	}

	@Override public void onPause() {
		super.onPause();
		mTimeLastPaused = SystemClock.uptimeMillis();
	}
	private long mTimeLastPaused;

	@Override public void onStop() {
		super.onStop();
		mViewModel.clearSelection();
	}

	@Override public void onDestroy() {
		IslandAppListProvider.getInstance(requireActivity()).unregisterObserver(mAppChangeObserver);
		super.onDestroy();
	}

	AppListProvider.PackageChangeObserver<IslandAppInfo> mAppChangeObserver = new AppListProvider.PackageChangeObserver<IslandAppInfo>() {

		@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package updated: " + apps);
			mViewModel.onPackagesUpdate(apps, mBinding.toolbar.getMenu());
// TODO
//			Snackbars.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app.getLabel()),
//					Snackbars.withAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(activity, pkg))).show();
			invalidateOptionsMenu();
		}

		@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package removed: " + apps);
			mViewModel.onPackagesRemoved(apps, mBinding.toolbar.getMenu());
			invalidateOptionsMenu();
		}
	};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final @Nullable ViewGroup container, final @Nullable Bundle saved_state) {
		final FragmentActivity activity = requireActivity();
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mBinding.setFeatured(mViewModel.mFeatured);
		mBinding.setGuide(mUserGuide);
		mBinding.setLifecycleOwner(this);
		activity.setActionBar(mBinding.actionbar);	// Must before attach
		mViewModel.initializeTabs(activity, mBinding.tabs);

		mBinding.executePendingBindings();		// This ensures all view state being fully restored
		return mBinding.getRoot();
	}

	@Override public void onDestroyView() {
		if (mIslandManagerConnection != null) {
			final Activity activity = getActivity();
			if (activity != null) activity.unbindService(mIslandManagerConnection);
			mIslandManagerConnection = null;
		}
		super.onDestroyView();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
		menu.findItem(R.id.menu_search).setOnActionExpandListener(mOnActionExpandListener);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		final boolean not_featured_tab = ! mViewModel.mFeatured.visible.getValue();
		final MenuItem.OnMenuItemClickListener tip = mUserGuide == null ? null : mUserGuide.getAvailableTip();
		menu.findItem(R.id.menu_tip).setVisible(not_featured_tab && tip != null).setOnMenuItemClickListener(tip);
		menu.findItem(R.id.menu_search).setVisible(not_featured_tab);
		menu.findItem(R.id.menu_filter).setVisible(not_featured_tab);
		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	private final MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

		@Override public boolean onMenuItemActionExpand(final MenuItem item) {
			final View action_view = item.getActionView();
			if (action_view instanceof SearchView) {
				final SearchView search_view = ((SearchView) action_view);
				search_view.setOnSearchClickListener(v -> mViewModel.onSearchClick((SearchView) v));
				search_view.setOnCloseListener(() -> mViewModel.onSearchViewClose());
				search_view.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
					@Override public boolean onQueryTextChange(final String text) {
						return mViewModel.onQueryTextChange(text);
					}
					@Override public boolean onQueryTextSubmit(final String query) {
						mViewModel.onQueryTextSubmit(query);
						item.collapseActionView();
						return true;
					}
				});
			}
			return true;
		}

		@Override public boolean onMenuItemActionCollapse(final MenuItem item) {
			final View action_view = item.getActionView();
			if (action_view instanceof SearchView) ((SearchView) action_view).setOnQueryTextListener(null);		// Prevent onQueryTextChange("") from invoked
			return true;
		}
	};

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
		if (id == R.id.menu_filter) mViewModel.mChipsVisible.setValue(! mViewModel.mChipsVisible.getValue());
		if (id == R.id.menu_settings) startActivity(new Intent(requireActivity(), SettingsActivity.class));
		else if (id == R.id.menu_test) TempDebug.run(requireActivity());
		else return super.onOptionsItemSelected(item);
		return true;
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private AppListBinding mBinding;
	private MainViewModel mViewModel;
	private @Nullable UserGuide mUserGuide;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
