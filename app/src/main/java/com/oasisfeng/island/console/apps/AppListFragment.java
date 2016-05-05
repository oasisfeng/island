package com.oasisfeng.island.console.apps;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.databinding.Observable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.oasisfeng.android.ui.AppLabelCache;
import com.oasisfeng.island.R;
import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppViewModel;
import com.oasisfeng.island.model.AppViewModel.State;
import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.shortcut.AppLaunchShortcut;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.support.design.widget.Snackbar.LENGTH_INDEFINITE;

/** The main UI - App list */
public class AppListFragment extends Fragment {

	private static final String KStateKeyRecyclerView = "apps.recycler.layout";
	/** System packages shown to user always even if no launcher activities */
	private static final Collection<String> sAlwaysVisibleSysPkgs = Collections.singletonList("com.google.android.gms");

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Activity activity = getActivity();
		mIslandManager = new IslandManager(activity);
		mViewModel = new AppListViewModel(mIslandManager);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);

		new IslandProvisioning(activity, mIslandManager).startProfileOwnerProvisioningIfNeeded();

		final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		activity.registerReceiver(mPackageEventsObserver, filter);

		final IntentFilter pkgs_filter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		pkgs_filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		activity.registerReceiver(mPackagesEventsObserver, pkgs_filter);

		loadAppList(false);
	}

	@Override public void onDestroy() {
		mViewModel.removeOnPropertyChangedCallback(onPropertyChangedCallback);
		getActivity().unregisterReceiver(mPackagesEventsObserver);
		getActivity().unregisterReceiver(mPackageEventsObserver);
		super.onDestroy();
	}

	private final BroadcastReceiver mPackageEventsObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData();
		if (data == null) return;
		final String pkg = data.getSchemeSpecificPart();
		if (pkg == null || context.getPackageName().equals(pkg)) return;

		final AppViewModel app_before = mViewModel.getApp(pkg);
		final boolean just_cloned = app_before != null && app_before.getState() == State.NotCloned;
		final AppViewModel app_after = mViewModel.updateApp(pkg);
		if (just_cloned && app_after != null && app_after.getState() == State.Alive && mIslandManager.isLaunchable(app_after.pkg))
			Snackbar.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app_after.name), LENGTH_INDEFINITE)
					.setAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(context, pkg)).show();
		invalidateOptionsMenu();
	}};

	private final BroadcastReceiver mPackagesEventsObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final String[] pkgs = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
		if (pkgs == null) return;
		for (final String pkg : pkgs)
			mViewModel.updateApp(pkg);
		invalidateOptionsMenu();
	}};

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
		setHasOptionsMenu(true);
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mBinding.appList.setLayoutManager(new LinearLayoutManager(getActivity()));
		return mBinding.getRoot();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		menu.findItem(R.id.menu_show_all).setChecked(mShowAllApps);
//		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_show_all:
			item.setChecked(mViewModel.include_sys_apps = mShowAllApps = ! item.isChecked());	// Toggle the checked state
			mViewModel.clearSelection();
			loadAppList(mShowAllApps);
			return true;
		case R.id.menu_destroy:
			mIslandManager.destroy(mViewModel.getNonSystemExclusiveCloneNames());
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override public void onStop() {
		mBinding.getApps().clearSelection();
		super.onStop();
	}

	private void loadAppList(final boolean all) {
		Log.d(TAG, all ? "Start loading app list (all)..." : "Start loading app list...");
		// Build app list if not yet (or invalidated)
		new AsyncTask<Void, Void, Map<String, ApplicationInfo>>() {
			@Override protected Map<String, ApplicationInfo> doInBackground(final Void... params) {
				final Activity activity = getActivity();
				if (activity == null) return Collections.emptyMap();
				return populateApps(all);
			}

			@Override protected void onPostExecute(final Map<String, ApplicationInfo> items) {
				fillAppViewModels(items);
			}
		}.execute();
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

	private Map<String, ApplicationInfo> populateApps(final boolean all) {
		final Activity activity = getActivity();

		final String this_pkg = activity.getPackageName();
		//noinspection WrongConstant
		final List<ApplicationInfo> installed_apps = activity.getPackageManager().getInstalledApplications(GET_UNINSTALLED_PACKAGES);
		final SystemAppsManager system_apps = new SystemAppsManager(activity, mIslandManager);
		final ImmutableList<ApplicationInfo> apps = FluentIterable.from(installed_apps)
				// TODO: Also include system apps with running services (even if no launcher activities)
				.filter(app -> ! this_pkg.equals(app.packageName))		// Exclude Island
				.filter(all ? Predicates.alwaysTrue() : app ->			// Filter for apps shown by default
						(app.flags & FLAG_SYSTEM) == 0 || sAlwaysVisibleSysPkgs.contains(app.packageName)
								|| (! system_apps.isCritical(app.packageName) && mIslandManager.isLaunchable(app.packageName)))
				// Cloned apps first to optimize the label and icon loading experience.
				.toSortedList(Ordering.explicit(true, false).onResultOf(info -> (info.flags & FLAG_INSTALLED) != 0));

		final Map<String, ApplicationInfo> app_vm_by_pkg = new LinkedHashMap<>();	// LinkedHashMap to preserve the order
		for (final ApplicationInfo app : apps)
			app_vm_by_pkg.put(app.packageName, app);
		return app_vm_by_pkg;
	}

	private void fillAppViewModels(final Map<String/* pkg */, ApplicationInfo> app_vms) {
		final Activity activity = getActivity();
		if (activity == null) return;
		final AppLabelCache cache = AppLabelCache.load(getActivity());
		mViewModel.removeAllApps();

		cache.loadLabelTextOnly(app_vms.keySet(), new AppLabelCache.LabelLoadCallback() {
			@Override public boolean isCancelled(final String pkg) {
				return false;
			}

			@Override public void onTextLoaded(final String pkg, final CharSequence text, final int flags) {
				Log.d(TAG, "onTextLoaded for " + pkg + ": " + text);
				AppViewModel item = mViewModel.getApp(pkg);
				if (item == null) {
					final boolean launchable = mIslandManager.isLaunchable(pkg);
					if (! launchable && ! mShowAllApps) return;		// TODO: Filter launchable later
					item = mViewModel.addApp(pkg, text, flags, launchable);
					Log.v(TAG, "Add: " + item.pkg);
				} else Log.v(TAG, "Replace: " + item.pkg);
			}

			@Override public void onError(final String pkg, final Throwable error) {
				Log.w(TAG, "Failed to load label for " + pkg, error);
			}

			@Override public void onIconLoaded(final String pkg, final Drawable icon) {}
		});
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private IslandManager mIslandManager;
	private AppListViewModel mViewModel;
	private AppListBinding mBinding;
	private boolean mShowAllApps;

	private static final String TAG = "AppListFragment";
}
