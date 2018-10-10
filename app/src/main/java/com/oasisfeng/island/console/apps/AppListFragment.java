package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProvider;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import com.oasisfeng.android.app.LifecycleFragment;
import com.oasisfeng.android.os.Loopers;
import com.oasisfeng.androidx.lifecycle.ViewModelProviders;
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
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.shuttle.ServiceShuttleContext;
import com.oasisfeng.island.tip.Tip;

import java.util.Collection;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import java9.util.Optional;

/** The main UI - App list */
@ParametersAreNonnullByDefault
public class AppListFragment extends LifecycleFragment {

	@Override public void onCreate(final @Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);	// To keep view-model (by keeping the view-model provider)
		setHasOptionsMenu(true);
		final Activity activity = getActivity();
		mServiceShuttleContext = new ServiceShuttleContext(activity);
		final ViewModelProvider provider = ViewModelProviders.of(this);
		mViewModel = provider.get(AppListViewModel.class);
		mViewModel.mFeatured = mFeaturedViewModel = provider.get(FeaturedListViewModel.class);
		mUserGuide = UserGuide.initializeIfNeeded(activity, this, mViewModel);
		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
	}

	@Override public void onResume() {
		super.onResume();
		if (SystemClock.uptimeMillis() - mTimeLastPaused < 1_000) return;	// Avoid updating for brief pausing caused by cross-profile functionality.
		if (mFeaturedViewModel.visible.getValue()) mFeaturedViewModel.update(getActivity());
		Loopers.addIdleTask(() -> Optional.ofNullable(getActivity()).map(Tip::next).ifPresent(mBinding::setCard));
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
		IslandAppListProvider.getInstance(getActivity()).unregisterObserver(mAppChangeObserver);
		super.onDestroy();
	}

	AppListProvider.PackageChangeObserver<IslandAppInfo> mAppChangeObserver = new AppListProvider.PackageChangeObserver<IslandAppInfo>() {

		@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package updated: " + apps);
			mViewModel.onPackagesUpdate(apps);
// TODO
//			Snackbars.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app.getLabel()),
//					Snackbars.withAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(activity, pkg))).show();
			invalidateOptionsMenu();
		}

		@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package removed: " + apps);
			mViewModel.onPackagesRemoved(apps);
			invalidateOptionsMenu();
		}
	};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final @Nullable ViewGroup container, final @Nullable Bundle saved_state) {
		final Activity activity = Objects.requireNonNull(getActivity());
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mBinding.setFeatured(mFeaturedViewModel);
		mBinding.setGuide(mUserGuide);
		mBinding.setLifecycleOwner(this);
		activity.setActionBar(mBinding.actionbar);	// Must before attach
		mViewModel.attach(activity, mBinding.toolbar.getMenu(), mBinding.bottomNavigation, saved_state);
		mViewModel.mSelection.observe(this, selection -> invalidateOptionsMenu());

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
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		final MenuItem.OnMenuItemClickListener tip = mUserGuide == null ? null : mUserGuide.getAvailableTip();
		menu.findItem(R.id.menu_tip).setVisible(tip != null).setOnMenuItemClickListener(tip);
		menu.findItem(R.id.menu_search).setVisible(mViewModel.mSelection.getValue() == null).setOnActionExpandListener(mOnActionExpandListener);
//		menu.findItem(R.id.menu_files).setVisible(context != null && Users.hasProfile() &&
//				(! Permissions.has(context, WRITE_EXTERNAL_STORAGE) || findFileBrowser(context) != null));
		menu.findItem(R.id.menu_show_system).setChecked(mViewModel.areSystemAppsIncluded());
		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	private final MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

		@Override public boolean onMenuItemActionExpand(final MenuItem item) {
			final View action_view = item.getActionView();
			if (action_view instanceof SearchView) ((SearchView) action_view).setOnQueryTextListener(mOnQueryTextListener);
			return true;
		}

		@Override public boolean onMenuItemActionCollapse(final MenuItem item) { return true; }
	};

	private final SearchView.OnQueryTextListener mOnQueryTextListener = new SearchView.OnQueryTextListener() {

		@Override public boolean onQueryTextChange(final String text) {
			mViewModel.onQueryTextChange(text);
			return true;
		}

		@Override public boolean onQueryTextSubmit(final String query) { return true; }
	};

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
		if (id == R.id.menu_show_system) {
			final boolean should_include = ! item.isChecked();
			mViewModel.onFilterHiddenSysAppsInclusionChanged(should_include);
			item.setChecked(should_include);    // Toggle the checked state
		} else if (id == R.id.menu_settings) startActivity(new Intent(getActivity(), SettingsActivity.class));
		else if (id == R.id.menu_test) TempDebug.run(getActivity());
		else return super.onOptionsItemSelected(item);
		return true;
	}

	@Override public void onSaveInstanceState(final Bundle out_state) {
		super.onSaveInstanceState(out_state);
		mViewModel.onSaveInstanceState(out_state);
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private AppListBinding mBinding;
	private AppListViewModel mViewModel;
	private FeaturedListViewModel mFeaturedViewModel;
	private @Nullable UserGuide mUserGuide;
	private ServiceShuttleContext mServiceShuttleContext;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
