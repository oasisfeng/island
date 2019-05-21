package com.oasisfeng.island.featured;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.app.LifecycleActivity;
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
import com.oasisfeng.island.controller.IslandAppClones;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.files.IslandFiles;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.FeaturedEntryBinding;
import com.oasisfeng.island.security.SecurityPrompt;
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.settings.SetupPreferenceFragment;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static androidx.lifecycle.Transformations.map;
import static androidx.recyclerview.widget.ItemTouchHelper.END;
import static androidx.recyclerview.widget.ItemTouchHelper.START;

/**
 * View-model for featured list
 *
 * Created by Oasis on 2018/5/18.
 */
@ParametersAreNonnullByDefault
public class FeaturedListViewModel extends AndroidViewModel {

	private static final String SCOPE_TAG_PREFIX_FEATURED = "featured_";
	private static final String PREF_KEY_ADB_SECURE_PROTECTED = "adb_secure_protected";
	private static final String PACKAGE_COOLAPK = "com.coolapk.market";
	private static final String PACKAGE_ICEBOX = "com.catchingnow.icebox";
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
		final LifecycleActivity activity = (LifecycleActivity) Objects.requireNonNull(Activities.findActivityFrom(context));
		final Application app = getApplication();
		final boolean is_device_owner = new DevicePolicies(context).isActiveDeviceOwner(), has_profile = Users.hasProfile();
		features.beginBatchedUpdates();
		features.clear();

		if (SHOW_ALL || IslandFiles.isCompatible(context)) {
			final boolean has_across_users_permission = Permissions.has(context, Permissions.INTERACT_ACROSS_USERS);
			if (! has_across_users_permission)
				addFeature(app, "file_shuttle_prereq", R.string.featured_file_shuttle_title, R.string.featured_file_shuttle_description, 0,
						R.string.dialog_button_learn_more, c -> WebContent.view(c, Config.URL_FILE_SHUTTLE.get()));
			else if (! Permissions.has(context, WRITE_EXTERNAL_STORAGE) || ! IslandFiles.isFileShuttleEnabled(context))
				addFeatureRaw(app, "file_shuttle", R.string.featured_file_shuttle_title, R.string.featured_file_shuttle_description,
						0, R.string.dialog_button_activate, vm -> IslandFiles.enableFileShuttle(activity));
			else {
				Analytics.$().setProperty(Analytics.Property.FileShuttleEnabled, "1");
				addFeaturedApp(R.string.featured_fx_title, R.string.featured_fx_description, R.drawable.ic_launcher_fx, "nextapp.fx");
			}
		}

		if (SHOW_ALL || ! is_device_owner)
			addFeature(app, "god_mode", R.string.featured_god_mode_title, R.string.featured_god_mode_description, 0,
					R.string.featured_button_setup, c -> SettingsActivity.startWithPreference(c, SetupPreferenceFragment.class));

		final boolean adb_enabled = "1".equals(Settings.Global.getString(app.getContentResolver(), Settings.Global.ADB_ENABLED));
		final LiveUserRestriction adb_secure = ! is_device_owner && ! has_profile ? null
				: new LiveUserRestriction(app, DISALLOW_DEBUGGING_FEATURES, is_device_owner ? Users.owner : Users.profile);
		if (adb_secure != null && (SHOW_ALL || adb_enabled || adb_secure.query(activity))) {	// ADB is disabled so long as ADB secure is enabled.
			addFeatureRaw(app, "adb_secure", is_device_owner ? R.string.featured_adb_secure_title : R.string.featured_adb_secure_island_title,
					R.string.featured_adb_secure_description,0, map(adb_secure, enabled -> enabled ? R.string.action_disable : R.string.action_enable),
					vm -> toggleAdbSecure(activity, vm, Objects.equals(vm.button.getValue(), R.string.action_enable), false));
		}

		addFeaturedApp(R.string.featured_greenify_title, R.string.featured_greenify_description, R.drawable.ic_launcher_greenify, "com.oasisfeng.greenify");
		addFeaturedApp(R.string.featured_saf_enhancer_title, R.string.featured_saf_enhancer_description, R.drawable.ic_launcher_saf_enhancer,
				"app.gwo.safenhancer.lite", "app.gwo.safenhancer");

		if (! addFeaturedApp(R.string.featured_icebox_title, R.string.featured_icebox_description, R.drawable.ic_launcher_icebox, PACKAGE_ICEBOX)
				&& Users.hasProfile() && IslandAppListProvider.getInstance(context).get(PACKAGE_ICEBOX, Users.profile) == null) {
			new Handler().postDelayed(() -> {	// Dirty workaround due to IslandAppListProvider updated after onResume()
				if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
						&& IslandAppListProvider.getInstance(activity).get(PACKAGE_ICEBOX, Users.profile) != null)
					update(context);
			}, 1_000);
			final IslandAppInfo icebox_in_mainland = IslandAppListProvider.getInstance(context).get(PACKAGE_ICEBOX, Users.owner);
			if (icebox_in_mainland != null) addFeature(app, "icebox", R.string.featured_icebox_title, R.string.featured_icebox_description,
					R.drawable.ic_launcher_icebox, R.string.action_clone, c -> IslandAppClones.cloneApp(context/* must be activity */, icebox_in_mainland));
		}

		addFeaturedApp(R.string.featured_appops_title, R.string.featured_appops_description, R.drawable.ic_launcher_appops,
				"rikka.appops", "rikka.appops.pro");

		if (SHOW_ALL || ! mApps.isInstalledBy(GooglePlayStore.PACKAGE_NAME)) {
			final boolean installed = Apps.of(context).isInstalledOnDevice(PACKAGE_COOLAPK);
			addFeature(app, "coolapk", R.string.featured_coolapk_title, R.string.featured_coolapk_description, R.drawable.ic_launcher_coolapk,
					installed ? 0 : R.string.featured_button_install, installed ? c -> Apps.of(c).launch(PACKAGE_COOLAPK) : c -> WebContent.view(c, Config.URL_COOLAPK.get()));
		}

		features.endBatchedUpdates();
	}

	private boolean addFeaturedApp(final @StringRes int title, final @StringRes int description, final @DrawableRes int icon, final String... pkgs) {
		if (! SHOW_ALL) for (final String pkg : pkgs) if (mApps.isInstalledInCurrentUser(pkg)) return false;
		final String pkg = pkgs[0];
		addFeature(getApplication(), pkg, title, description, icon, R.string.featured_button_install, c -> showInMarket(c, pkg));
		return true;
	}

	private void toggleAdbSecure(final LifecycleActivity activity, final FeaturedViewModel vm, final boolean enabling, final boolean security_confirmed) {
		if (! enabling && ! security_confirmed && SDK_INT >= M && isAdbSecureProtected(activity)) {
			requestSecurityConfirmationBeforeDisablingAdbSecure(activity, vm);
			return;
		}

		final DevicePolicies policies = new DevicePolicies(activity);
		if (policies.isActiveDeviceOwner()) {
			if (! enabling) {
				policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
				policies.execute(DevicePolicyManager::setGlobalSetting, Settings.Global.ADB_ENABLED, "1");	// DISALLOW_DEBUGGING_FEATURES also disables ADB.
			} else policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
		}
		if (! Users.hasProfile()) {
			showPromptForAdbSecureProtection(activity, enabling);
			return;		// No managed profile, all done.
		}

		final Context app_context = getApplication();
		MethodShuttle.runInProfile(app_context, () -> {
			final DevicePolicies device_policies = new DevicePolicies(app_context);	// The "policies" instance can not be passed into profile.
			if (enabling) device_policies.execute(DevicePolicyManager::addUserRestriction, DISALLOW_DEBUGGING_FEATURES);
			else device_policies.execute(DevicePolicyManager::clearUserRestriction, DISALLOW_DEBUGGING_FEATURES);
			return enabling;
		}).whenComplete((enabled, e) -> {
			final Context context = getApplication();
			if (e != null) {
				Analytics.$().logAndReport(TAG, "Error setting featured button", e);
				Toast.makeText(context, R.string.toast_internal_error, Toast.LENGTH_LONG).show();
			} else {
				LiveUserRestriction.requestUpdate(context);	// Request explicit update, since observer does not work across users.
				showPromptForAdbSecureProtection(activity, enabled);
			}
		});
	}

	private void showPromptForAdbSecureProtection(final LifecycleActivity activity, final boolean enabled) {
		if (SDK_INT < M || ! enabled || activity.isDestroyed()) return;
		if (isAdbSecureProtected(activity)) Snackbars.make(activity, R.string.prompt_security_confirmation_activated)
				.setAction(R.string.action_deactivate, v -> disableSecurityConfirmationForAdbSecure(activity)).show();
		else Snackbars.make(activity, R.string.prompt_security_confirmation_suggestion)
				.setAction(R.string.action_activate, v -> enableSecurityConfirmationForAdbSecure(activity)).show();
	}

	private static boolean isAdbSecureProtected(final Context context) {
		return getDefaultSharedPreferences(context).getBoolean(PREF_KEY_ADB_SECURE_PROTECTED, false);
	}

	@RequiresApi(M) private void enableSecurityConfirmationForAdbSecure(final LifecycleActivity activity) {
		if (activity.isDestroyed()) return;
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_activating, () -> {
			getDefaultSharedPreferences(getApplication()).edit().putBoolean(PREF_KEY_ADB_SECURE_PROTECTED, true).apply();
			Toast.makeText(getApplication(), R.string.toast_security_confirmation_activated, Toast.LENGTH_SHORT).show();
		});
	}

	@RequiresApi(M) private void disableSecurityConfirmationForAdbSecure(final LifecycleActivity activity) {
		if (activity.isDestroyed()) return;
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_deactivating, () -> {
			getDefaultSharedPreferences(getApplication()).edit().putBoolean(PREF_KEY_ADB_SECURE_PROTECTED, false).apply();
			Toast.makeText(getApplication(), R.string.toast_security_confirmation_deactivated, Toast.LENGTH_SHORT).show();
		});
	}

	@RequiresApi(M) private void requestSecurityConfirmationBeforeDisablingAdbSecure(final LifecycleActivity activity, final FeaturedViewModel vm) {
		SecurityPrompt.showBiometricPrompt(activity, R.string.featured_adb_secure_title, R.string.prompt_security_confirmation_to_disable,
				() -> toggleAdbSecure(activity, vm, false, true));
	}

	private static void showInMarket(final Context context, final String pkg) {
		Analytics.$().event("featured_install").with(Analytics.Param.ITEM_ID, pkg).send();
		Apps.of(context).showInMarket(pkg, "island", "featured");
	}

	private void addFeature(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							final @DrawableRes int icon, final @StringRes int button, final Consumer<Context> function) {
		addFeatureRaw(app, tag, title, description, icon, button, vm -> function.accept(vm.getApplication()));
	}

	private void addFeatureRaw(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							   final @DrawableRes int icon, final @StringRes int button, final Consumer<FeaturedViewModel> function) {
		addFeatureRaw(app, tag, title, description, icon, new NonNullMutableLiveData<>(button), function);
	}

	private void addFeatureRaw(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							   final @DrawableRes int icon, final LiveData<Integer> button, final Consumer<FeaturedViewModel> function) {
		features.add(new FeaturedViewModel(app, sOrderGenerator.incrementAndGet(), tag, app.getString(title), app.getText(description),
				icon != 0 ? app.getDrawable(icon) : null, button, function, Scopes.app(app).isMarked(SCOPE_TAG_PREFIX_FEATURED + tag)));
	}

	public FeaturedListViewModel(final Application app) { super(app); mApps = Apps.of(app); }

	private final Apps mApps;

	private static final AtomicInteger sOrderGenerator = new AtomicInteger();
	private static final String TAG = "FLVM";
}
