package com.oasisfeng.island.console.apps;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.databinding.Observable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
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
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.ClonedHiddenSystemApps;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.AppListBinding;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.shuttle.ShuttleServiceConnection;
import com.oasisfeng.island.util.Users;

import java.util.Collection;
import java.util.List;

import java8.util.stream.Collectors;

/** The main UI - App list */
public class AppListFragment extends Fragment implements AppListViewModel.IAppListAction {

	private static final String STATE_KEY_RECYCLER_VIEW = "apps.recycler.layout";

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		final Activity activity = getActivity();

		mIslandManager = new IslandManager(activity);
		mViewModel = new AppListViewModel();
		mViewModel.mProfileController = IslandManager.NULL;

		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
	}

	@Override public void onStart() {
		super.onStart();
		mShuttleContext = new ShuttleContext(getActivity());
		if (GlobalStatus.hasProfile() && ! Services.bind(mShuttleContext, IIslandManager.class, mServiceConnection))
			Toast.makeText(getActivity(), "Error opening Island", Toast.LENGTH_LONG).show();
	}

	@Override public void onStop() {
		mViewModel.mProfileController = IslandManager.NULL;
		if (GlobalStatus.hasProfile()) try {
			mShuttleContext.unbindService(mServiceConnection);
		} catch (final RuntimeException e) { Log.e(TAG, "Unexpected exception in unbinding", e); }
		mShuttleContext = null;
		mViewModel.clearSelection();
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
			mViewModel.mProfileController = IIslandManager.Stub.asInterface(service);
			Log.d(TAG, "Service connected");
		}

		@Override public void onServiceDisconnected() {
			mViewModel.mProfileController = IslandManager.NULL;
		}
	};

	@Override public void onResume() {
		super.onResume();
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

	private final Observable.OnPropertyChangedCallback onPropertyChangedCallback = new Observable.OnPropertyChangedCallback() {
		@Override public void onPropertyChanged(final Observable observable, final int var) {
			if (var == BR.selection) invalidateOptionsMenu();
		}
	};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final @Nullable Bundle saved_state) {
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mViewModel.attach(getActivity(), mBinding.details.toolbar.getMenu(), mBinding.drawerContent.drawerFilter, saved_state);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);
        mViewModel.setAppListAction(this);

		if (! Services.bind(getActivity(), IIslandManager.class, mIslandManagerConnection = new ServiceConnection() {
			@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
				mViewModel.setOwnerController(IIslandManager.Stub.asInterface(service));
			}

			@Override public void onServiceDisconnected(final ComponentName name) {}
		})) throw new IllegalStateException("Module engine not installed");

		getActivity().setActionBar(mBinding.appbar);
		final ActionBar actionbar = getActivity().getActionBar();
		if (actionbar != null) {
			ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(getActivity(),
					mBinding.drawer, R.string.drawer_icon_open, R.string.drawer_icon_close);
			mBinding.drawer.addDrawerListener(drawerToggle);
			actionbar.setDisplayHomeAsUpEnabled(true);
			actionbar.setHomeButtonEnabled(true);
			drawerToggle.syncState();
            mBinding.appbar.setNavigationOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mBinding.drawer.openDrawer(GravityCompat.START);
				}
			});
		}
		//mBinding.filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
		//	@Override public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
		//		final Activity activity = getActivity();
		//		if (activity == null) return;
		//		mViewModel.onFilterPrimaryChanged(position);
		//	}
		//	@Override public void onNothingSelected(final AdapterView<?> parent) {}
		//});
		return mBinding.getRoot();
	}

	@Override public void onDestroyView() {
		if (mIslandManagerConnection != null) {
			getActivity().unbindService(mIslandManagerConnection);
			mIslandManagerConnection = null;
		}
		super.onDestroyView();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		menu.findItem(R.id.menu_show_system).setChecked(mViewModel.areSystemAppsIncluded());
		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
		if (id == R.id.menu_show_system) {
			final boolean should_include = ! item.isChecked();
			mViewModel.onFilterHiddenSysAppsInclusionChanged(should_include);
			item.setChecked(should_include);	// Toggle the checked state
			return true;
		} else if (id == R.id.menu_test) {
			TempDebug.run(getActivity());
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override public void onSaveInstanceState(final Bundle out_state) {
		super.onSaveInstanceState(out_state);
		final RecyclerView.LayoutManager layout_manager = mBinding.appList.getLayoutManager();
		if (layout_manager != null) out_state.putParcelable(STATE_KEY_RECYCLER_VIEW, layout_manager.onSaveInstanceState());
		mViewModel.onSaveInstanceState(out_state);
	}

	@Override public void onViewStateRestored(final Bundle saved_state) {
		super.onViewStateRestored(saved_state);
		if (saved_state == null) return;
		mBinding.appList.getLayoutManager().onRestoreInstanceState(saved_state.getParcelable(STATE_KEY_RECYCLER_VIEW));
	}


	@Override
	public void onDestroyClick() {
		final Activity activity = getActivity();
		final IslandAppListProvider provider = IslandAppListProvider.getInstance(activity);
		final List<String> exclusive_clones = provider.installedApps()
				.filter(app -> Users.isProfile(app.user) && ! app.isSystem() && provider.isExclusive(app))
				.map(AppInfo::getLabel).collect(Collectors.toList());

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
		final IIslandManager controller = mViewModel.mProfileController;
		if (controller != null) try {
			controller.destroyProfile();
			ClonedHiddenSystemApps.reset(activity, GlobalStatus.profile);
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
	private ShuttleContext mShuttleContext;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
