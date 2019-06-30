package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
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
import com.oasisfeng.island.tip.Tip;

import java.util.Collection;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import java9.util.Optional;

/** The main UI - App list */
@ParametersAreNonnullByDefault
public class AppListFragment extends LifecycleFragment {

	@Override public void onCreate(final @Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);	// To keep view-model (by keeping the view-model provider)
		setHasOptionsMenu(true);
		final Activity activity = getActivity();
		final ViewModelProvider provider = ViewModelProviders.of(this);
		mViewModel = provider.get(AppListViewModel.class);
		mViewModel.mFeatured = provider.get(FeaturedListViewModel.class);
		mUserGuide = UserGuide.initializeIfNeeded(activity, this, mViewModel);
		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
		mViewModel.mFeatured.visible.observe(this, visible -> invalidateOptionsMenu());
	}

	@Override public void onResume() {
		super.onResume();
		if (SystemClock.uptimeMillis() - mTimeLastPaused < 1_000) return;	// Avoid updating for brief pausing caused by cross-profile functionality.
		if (mViewModel.mFeatured.visible.getValue()) mViewModel.mFeatured.update(getActivity());
		Loopers.addIdleTask(() -> AsyncTask.execute(() -> Optional.ofNullable(getActivity()).map(Tip::next).ifPresent(mBinding::setCard)));
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
		mBinding.setFeatured(mViewModel.mFeatured);
		mBinding.setGuide(mUserGuide);
		mBinding.setLifecycleOwner(this);
		activity.setActionBar(mBinding.actionbar);	// Must before attach
		mViewModel.attach(activity, mBinding.toolbar.getMenu(), mBinding.bottomNavigation, saved_state != null ? saved_state : getArguments());
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
		if (id == R.id.menu_settings) startActivity(new Intent(getActivity(), SettingsActivity.class));
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
	private @Nullable UserGuide mUserGuide;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
