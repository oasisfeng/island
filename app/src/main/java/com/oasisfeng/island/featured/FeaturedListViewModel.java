package com.oasisfeng.island.featured;

import android.app.Activity;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.databinding.recyclerview.BindingRecyclerViewAdapter;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.android.google.GooglePlayStore;
import com.oasisfeng.android.ui.Snackbars;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.util.Consumer;
import com.oasisfeng.androidx.lifecycle.NonNullMutableLiveData;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.files.IslandFiles;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.FeaturedEntryBinding;
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.settings.SetupPreferenceFragment;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;
import static androidx.recyclerview.widget.ItemTouchHelper.END;
import static androidx.recyclerview.widget.ItemTouchHelper.START;

/**
 * View-model for featured list
 *
 * Created by Oasis on 2018/5/18.
 */
public class FeaturedListViewModel extends AndroidViewModel {

	private static final String SCOPE_TAG_PREFIX_FEATURED = "featured_";
	private static final boolean SHOW_ALL = false;		// For debugging purpose

	public NonNullMutableLiveData<Boolean> visible = new NonNullMutableLiveData<>(Boolean.FALSE);
	public final ObservableSortedList<FeaturedViewModel> features = new ObservableSortedList<>(FeaturedViewModel.class);

	public final ItemBinder<FeaturedViewModel> item_binder = (container, model, binding) -> binding.setVariable(BR.vm, model);

	public final ItemTouchHelper item_touch_helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, START | END) {

		@Override public void onSwiped(final RecyclerView.ViewHolder holder, final int direction) {
			final FeaturedViewModel vm = ((FeaturedEntryBinding) ((BindingRecyclerViewAdapter.ViewHolder) holder).binding).getVm();
			final int index = features.indexOf(vm);
			final boolean mark_read = direction != START || ! vm.dismissed.getValue();		// Left-swipe to mark-unread for already read entry
			vm.dismissed.setValue(mark_read);
			features.updateItemAt(index, vm);
			final Scopes.Scope app_scope = Scopes.app(holder.itemView.getContext());
			final String scope_tag = SCOPE_TAG_PREFIX_FEATURED + vm.tag;
			if (mark_read) app_scope.markOnly(scope_tag);
			else app_scope.unmark(scope_tag);
		}

		@Override public boolean onMove(final RecyclerView view, final RecyclerView.ViewHolder vh, final RecyclerView.ViewHolder vht) { return false; }
	});

	public void update(final Context context) {
		final Apps apps = Apps.of(context);
		final Activity activity = Objects.requireNonNull(Activities.findActivityFrom(context));
		final Application app = getApplication();
		final boolean is_device_owner = new DevicePolicies(context).isActiveDeviceOwner(), has_profile = Users.hasProfile();
		features.beginBatchedUpdates();
		features.clear();

		final boolean file_shuttle_compatible = IslandFiles.isCompatible(context);
		if (SHOW_ALL || file_shuttle_compatible) {
			final boolean has_across_users_permission = Permissions.has(context, Permissions.INTERACT_ACROSS_USERS);
			if (! has_across_users_permission)
				addFeature(app, "file_shuttle_prereq", R.string.featured_file_shuttle_title, R.string.featured_file_shuttle_description, 0,
						R.string.dialog_button_learn_more, c -> WebContent.view(c, Config.URL_FILE_SHUTTLE.get()));
			else if (! Permissions.has(context, WRITE_EXTERNAL_STORAGE) || ! IslandFiles.isFileShuttleEnabled(context))
				addFeatureRaw(activity.getApplication(), "file_shuttle", R.string.featured_file_shuttle_title, R.string.featured_file_shuttle_description,
						0, R.string.dialog_button_activate, vm -> IslandFiles.enableFileShuttle(activity));
			else {
				Analytics.$().setProperty(Analytics.Property.FileShuttleEnabled, "1");
				if (SHOW_ALL || ! apps.isInstalledInCurrentUser("nextapp.fx"))
					addFeature(app, "fx", R.string.featured_fx_title, R.string.featured_fx_description, R.drawable.ic_launcher_fx,
							R.string.featured_button_install, c -> showInMarket(c, "nextapp.fx"));
			}

		}

		if (SHOW_ALL || ! is_device_owner)
			addFeature(app, "god_mode", R.string.featured_god_mode_title, R.string.featured_god_mode_description, 0,
					R.string.featured_button_setup, c -> SettingsActivity.startWithPreference(c, SetupPreferenceFragment.class));

		final UserManager um = Objects.requireNonNull((UserManager) app.getSystemService(Context.USER_SERVICE));
		final boolean adb_secure_enabled = (is_device_owner && um.getUserRestrictions(Users.owner).containsKey(DISALLOW_DEBUGGING_FEATURES)
				|| has_profile && um.getUserRestrictions(Users.profile).containsKey(DISALLOW_DEBUGGING_FEATURES));
		if (SHOW_ALL || adb_secure_enabled || "1".equals(Settings.Global.getString(app.getContentResolver(), Settings.Global.ADB_ENABLED)))
			addFeatureRaw(app, "adb_secure", is_device_owner ? R.string.featured_adb_secure_title : R.string.featured_adb_secure_island_title,
					R.string.featured_adb_secure_description, 0, adb_secure_enabled ? R.string.featured_button_disable : R.string.featured_button_enable,
					FeaturedListViewModel::toggleAdbSecure);

		if (SHOW_ALL || ! apps.isInstalledInCurrentUser("com.oasisfeng.greenify"))
			addFeature(app, "greenify", R.string.featured_greenify_title, R.string.featured_greenify_description, R.drawable.ic_launcher_greenify,
					R.string.featured_button_install, c -> showInMarket(c, "com.oasisfeng.greenify"));

		if (SHOW_ALL || ! apps.isInstalledInCurrentUser("com.catchingnow.icebox") && is_device_owner)
			addFeature(app, "icebox", R.string.featured_icebox_title, R.string.featured_icebox_description, R.drawable.ic_launcher_icebox,
					R.string.featured_button_install, c -> showInMarket(c, "com.catchingnow.icebox"));

		if (SHOW_ALL || ! apps.isInstalledInCurrentUser("rikka.appops") && ! apps.isInstalledInCurrentUser("rikka.appops.pro"))
			addFeature(app, "appops", R.string.featured_appops_title, R.string.featured_appops_description, R.drawable.ic_launcher_appops,
					R.string.featured_button_install, c -> showInMarket(c, "rikka.appops"));

		if (SHOW_ALL || ! apps.isInstalledBy(GooglePlayStore.PACKAGE_NAME)) {
			final boolean installed = Apps.of(context).isInstalledOnDevice(PACKAGE_COOLAPK);
			addFeature(app, "coolapk", R.string.featured_coolapk_title, R.string.featured_coolapk_description, R.drawable.ic_launcher_coolapk,
					installed ? 0 : R.string.featured_button_install, installed ? null : c -> WebContent.view(c, Config.URL_COOLAPK.get()));
		}

		features.endBatchedUpdates();
	}

	private static void toggleAdbSecure(final FeaturedViewModel vm) {
		final Context context = vm.getApplication();
		final boolean enabling = vm.button.getValue() == R.string.featured_button_enable;
		final DevicePolicies policies = new DevicePolicies(context);
		if (policies.isActiveDeviceOwner()) {
			if (! enabling) {
				policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
				policies.execute(DevicePolicyManager::setGlobalSetting, Settings.Global.ADB_ENABLED, "1");	// DISALLOW_DEBUGGING_FEATURES also disables ADB.
			} else policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
		}
		if (! Users.hasProfile()) {		// No managed profile, all done.
			vm.button.setValue(enabling ? R.string.featured_button_disable : R.string.featured_button_enable);
			return;
		}

		MethodShuttle.runInProfile(context, () -> {
			final DevicePolicies device_policies = new DevicePolicies(context);		// The "policies" instance can not be passed into profile.
			if (enabling) device_policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
			else device_policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
			return enabling;
		}).whenComplete((result, e) -> {
			if (e != null) {
				Analytics.$().logAndReport(TAG, "Error setting featured button", e);
				Toast.makeText(context, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
			}
			vm.button.setValue(result ? R.string.featured_button_disable : R.string.featured_button_enable);
		});
	}

	private static void showInMarket(final Context context, final String pkg) {
		Analytics.$().event("featured_install").with(Analytics.Param.ITEM_ID, pkg).send();
		Apps.of(context).showInMarket(pkg, "island", "featured");
	}

	private static final String PACKAGE_COOLAPK = "com.coolapk.market";

	private void addFeature(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							final @DrawableRes int icon, final @StringRes int button, final Consumer<Context> function) {
		addFeatureRaw(app, tag, title, description, icon, button, vm -> function.accept(vm.getApplication()));
	}

	private void addFeatureRaw(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							   final @DrawableRes int icon, final @StringRes int button, final Consumer<FeaturedViewModel> function) {
		features.add(new FeaturedViewModel(app, sOrderGenerator.incrementAndGet(), tag, app.getString(title), app.getText(description),
				icon != 0 ? app.getDrawable(icon) : null, button, function, Scopes.app(app).isMarked(SCOPE_TAG_PREFIX_FEATURED + tag)));
	}

	public FeaturedListViewModel(final Application app) { super(app); }

	private static final AtomicInteger sOrderGenerator = new AtomicInteger();
	private static final String TAG = "FLVM";
}
