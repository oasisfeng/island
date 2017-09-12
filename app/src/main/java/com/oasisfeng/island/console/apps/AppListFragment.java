package com.oasisfeng.island.console.apps;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.databinding.Observable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SearchView;
import android.widget.Toast;

import com.oasisfeng.android.content.pm.Permissions;
import com.oasisfeng.android.service.Services;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.guide.UserGuide;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.AppListBinding;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.shuttle.ShuttleServiceConnection;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;

import java.util.Collection;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/** The main UI - App list */
public class AppListFragment extends Fragment {

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		final Activity activity = getActivity();
		mShuttleContext = new ShuttleContext(activity);
		mViewModel = new AppListViewModel();
		mViewModel.mProfileController = IslandManager.NULL;
		mUserGuide = UserGuide.initializeIfNeeded(activity, mViewModel);
		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
	}

	@Override public void onStart() {
		super.onStart();
		if (Users.hasProfile() && ! Services.bind(mShuttleContext, IIslandManager.class, mServiceConnection))
			Toast.makeText(getActivity(), "Error opening Island", Toast.LENGTH_LONG).show();
	}

	@Override public void onStop() {
		mViewModel.mProfileController = IslandManager.NULL;
		if (Users.hasProfile()) try {
			mShuttleContext.unbindService(mServiceConnection);
		} catch (final RuntimeException e) { Log.e(TAG, "Unexpected exception in unbinding", e); }
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
			Log.v(TAG, "Service connected");
		}

		@Override public void onServiceDisconnected() {
			mViewModel.mProfileController = IslandManager.NULL;
		}
	};

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

	private final Observable.OnPropertyChangedCallback onPropertyChangedCallback = new Observable.OnPropertyChangedCallback() { @Override public void onPropertyChanged(final Observable observable, final int var) {
		if (var == BR.selection) invalidateOptionsMenu();
	}};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final @Nullable Bundle saved_state) {
		final Activity activity = getActivity();
		final AppListBinding binding = AppListBinding.inflate(inflater, container, false);
		binding.setApps(mViewModel);
		binding.setGuide(mUserGuide);
		mViewModel.attach(activity, binding.details.toolbar.getMenu(), saved_state);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);

		if (! Services.bind(activity, IIslandManager.class, mIslandManagerConnection = new ServiceConnection() {
			@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
				mViewModel.setOwnerController(IIslandManager.Stub.asInterface(service));
			}

			@Override public void onServiceDisconnected(final ComponentName name) {}
		})) throw new IllegalStateException("Module engine not installed");

		activity.setActionBar(binding.appbar);
		final ActionBar actionbar = activity.getActionBar();
		if (actionbar != null) actionbar.setDisplayShowTitleEnabled(false);
		binding.filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
				if (getActivity() == null) return;
				mViewModel.setFilterPrimaryChoice(position);
			}
			@Override public void onNothingSelected(final AdapterView<?> parent) {}
		});
		binding.executePendingBindings();		// This ensures all view state being fully restored
		return binding.getRoot();
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
		final Context context = getActivity();
		final MenuItem.OnMenuItemClickListener tip = mUserGuide == null ? null : mUserGuide.getAvailableTip();
		menu.findItem(R.id.menu_tip).setVisible(tip != null).setOnMenuItemClickListener(tip);
		menu.findItem(R.id.menu_search).setVisible(mViewModel.getSelection() == null).setOnActionExpandListener(mOnActionExpandListener);
		menu.findItem(R.id.menu_files).setVisible(context != null && Users.hasProfile() &&
				(! Permissions.has(context, WRITE_EXTERNAL_STORAGE) || findFileBrowser(context) != null));
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
		else if (id == R.id.menu_files) requestPermissionAndLaunchFilesExplorerInIsland();
		else if (id == R.id.menu_test) TempDebug.run(getActivity());
		else return super.onOptionsItemSelected(item);
		return true;
	}

	private void requestPermissionAndLaunchFilesExplorerInIsland() {
		final Context context = getActivity();
		if (context.checkPermission(WRITE_EXTERNAL_STORAGE, Process.myPid(), Process.myUid()) == PERMISSION_GRANTED) {
			new MethodShuttle(context).runInProfile(() -> {
				final Intent intent = findFileBrowser(context);
				if (intent == null) return false;
				final ComponentName component = intent.getComponent();	// Intent should be resolved already in findFileBrowser().
				final String pkg = component != null ? component.getPackageName() : null;
				if (pkg != null) {        // Unfreeze the target app (the intent is resolved against apps including frozen ones)
					final DevicePolicies policies = new DevicePolicies(context);
					try {
						policies.enableSystemApp(component.getPackageName());
					} catch (final IllegalArgumentException e) {	// Thrown if it is not system app
						policies.setApplicationHidden(component.getPackageName(), false);
					}
				}
				try {
					context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
							.putExtra(EXTRA_SHOW_FILESIZE, true).putExtra(EXTRA_SHOW_ADVANCED, true)
							.putExtra(EXTRA_FANCY_FEATURES, true).putExtra(EXTRA_ALLOW_MULTIPLE, true));
					Analytics.$().event("launch_file_browser").with(ITEM_CATEGORY, pkg).with(ITEM_ID, intent.toString()).send();
					return true;
				} catch (final ActivityNotFoundException e) { return false; }
			}, result -> {
				if (result != null && result) return;
				Toast.makeText(context, R.string.toast_file_shuttle_without_browser, Toast.LENGTH_LONG).show();
				Analytics.$().event("no_file_browser").send();
			});
			return;
		}

		if (SDK_INT >= M) {
			new MethodShuttle(context).runInProfile(() -> new DevicePolicies(context)		// Permission is implicitly granted
					.setPermissionGrantState(context.getPackageName(), WRITE_EXTERNAL_STORAGE, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED));
			requestPermissions(new String[] { WRITE_EXTERNAL_STORAGE }, 0);
		} else onRequestPermissionsResult(0, new String[0], new int[0]);
	}

	private static final String EXT_STORAGE_AUTHORITY = "com.android.externalstorage.documents";
	private static final String ACTION_BROWSE_DOC_ROOT = "android.provider.action.BROWSE_DOCUMENT_ROOT";
	private static final String ACTION_BROWSE = "android.provider.action.BROWSE";
	private static final String EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED";	// Disclosed from DocumentsContract
	private static final String EXTRA_SHOW_FILESIZE = "android.content.extra.SHOW_FILESIZE";
	private static final String EXTRA_FANCY_FEATURES = "android.content.extra.FANCY";

	private static Intent findFileBrowser(final Context context) {
		final PackageManager pm = context.getPackageManager();

		// Internal API of AOSP Documents UI app with internal path protocol of ExternalStorageProvider on Android 6+. (also back-ported to 5.x in MIUI)
		final Uri ext_storage_uri = new Uri.Builder().scheme("content").authority(EXT_STORAGE_AUTHORITY).path("/root/primary").build();
		final String type = context.getContentResolver().getType(ext_storage_uri);
		if (DocumentsContract.Root.MIME_TYPE_ITEM.equals(type)) {	// Although introduced in Android M, but ACTION_BROWSE is also back-ported by MIUI.
			final Intent intent = new Intent(ACTION_BROWSE).setDataAndType(ext_storage_uri, type);
			if (resolveIncludingFrozen(pm, intent) || resolveIncludingFrozen(pm, intent.setAction(ACTION_BROWSE_DOC_ROOT))) return intent;
		} else {	// Probably the internal path protocol (/root/primary) is not matched, try starting the responsible activity explicitly.
			@SuppressLint("InlinedApi") final String type_root = DocumentsContract.Root.MIME_TYPE_ITEM;		// It's hidden until Android O.
			final Intent intent = new Intent(ACTION_BROWSE).setType(type_root);
			if (resolveIncludingFrozen(pm, intent) || resolveIncludingFrozen(pm, intent.setAction(ACTION_BROWSE_DOC_ROOT))) return intent;
		}

		// General attempt to launch 3rd-party file browser.
		final Uri uri = Uri.fromFile(Environment.getExternalStorageDirectory());
		final Intent intent = new Intent(ACTION_VIEW).setDataAndType(uri, "resource/folder"/* required by some */);
		if (resolveIncludingFrozen(pm, intent)) return intent;

		// Last resort, barely only a browser for files.
		final Intent doc_open_intent = new Intent(ACTION_OPEN_DOCUMENT).setType("*/*");
		if (resolveIncludingFrozen(pm, doc_open_intent)) return doc_open_intent;

		return null;
	}

	private static boolean resolveIncludingFrozen(final PackageManager pm, final Intent intent) {
		final List<ResolveInfo> resolves = pm.queryIntentActivities(intent, MATCH_DEFAULT_ONLY | GET_UNINSTALLED_PACKAGES);
		for (final ResolveInfo resolve : resolves)
			if (resolve != null && (resolve.activityInfo.applicationInfo.flags & FLAG_INSTALLED) != 0)	// Only installed (including frozen).
				return intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)) != null;
		return false;
	}

	@Override public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
		if (permissions.length != 0 && grantResults.length != 0 && grantResults[0] == PERMISSION_GRANTED) {
			requestPermissionAndLaunchFilesExplorerInIsland();
		} else Toast.makeText(getActivity(), R.string.toast_external_storage_permission_required, Toast.LENGTH_LONG).show();
	}

	@Override public void onSaveInstanceState(final Bundle out_state) {
		super.onSaveInstanceState(out_state);
		mViewModel.onSaveInstanceState(out_state);
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private AppListViewModel mViewModel;
	private @Nullable UserGuide mUserGuide;
	private ShuttleContext mShuttleContext;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
