package com.oasisfeng.island.featured;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.databinding.recyclerview.BindingRecyclerViewAdapter;
import com.oasisfeng.android.databinding.recyclerview.ItemBinder;
import com.oasisfeng.android.google.GooglePlayStore;
import com.oasisfeng.android.ui.WebContent;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.util.Consumer;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.FeaturedEntryBinding;
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.settings.SetupPreferenceFragment;
import com.oasisfeng.island.util.DevicePolicies;

import java.util.concurrent.atomic.AtomicInteger;

import static android.support.v7.widget.helper.ItemTouchHelper.END;
import static android.support.v7.widget.helper.ItemTouchHelper.START;

/**
 * View-model for featured list
 *
 * Created by Oasis on 2018/5/18.
 */
public class FeaturedListViewModel extends AndroidViewModel {

	private static final String SCOPE_TAG_PREFIX_FEATURED = "featured_";
	private static final boolean ALWAYS_SHOW_ALL = false;		// For debugging purpose

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

	public FeaturedListViewModel(final Application app) {
		super(app);
		final Apps apps = Apps.of(app);
		final boolean is_device_owner = new DevicePolicies(app).isActiveDeviceOwner();
		features.beginBatchedUpdates();

		if (ALWAYS_SHOW_ALL || ! is_device_owner)
			addFeature(app, "god_mode", R.string.featured_god_mode_title, R.string.featured_god_mode_description, 0,
					R.string.featured_button_setup, context -> SettingsActivity.startWithPreference(context, SetupPreferenceFragment.class));

		if (ALWAYS_SHOW_ALL || ! apps.isInstalledInCurrentUser("com.oasisfeng.greenify"))
			addFeature(app, "greenify", R.string.featured_greenify_title, R.string.featured_greenify_description, R.drawable.ic_launcher_greenify,
					R.string.featured_button_install, context -> Apps.of(context).showInMarket("com.oasisfeng.greenify", "island", "featured"));

		if (ALWAYS_SHOW_ALL || ! apps.isInstalledInCurrentUser("com.catchingnow.icebox") && is_device_owner)
			addFeature(app, "icebox", R.string.featured_icebox_title, R.string.featured_icebox_description, R.drawable.ic_launcher_icebox,
					R.string.featured_button_install, context -> Apps.of(context).showInMarket("com.catchingnow.icebox", "island", "featured"));

		if (ALWAYS_SHOW_ALL || ! apps.isInstalledBy(GooglePlayStore.PACKAGE_NAME) && ! apps.isInstalledInCurrentUser("com.coolapk.market"))
			addFeature(app, "coolapk", R.string.featured_coolapk_title, R.string.featured_coolapk_description, R.drawable.ic_launcher_coolapk,
					R.string.featured_button_install, context -> WebContent.view(context, "https://www.coolapk.com/apk/com.coolapk.market?utm_source=island&utm_campaign=featured"));

		features.endBatchedUpdates();
	}

	private void addFeature(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							final @DrawableRes int icon, final @StringRes int button, final Consumer<Context> function) {
		addFeatureRaw(app, tag, title, description, icon, button, vm -> function.accept(vm.getApplication()));
	}

	private void addFeatureRaw(final Application app, final String tag, final @StringRes int title, final @StringRes int description,
							   final @DrawableRes int icon, final @StringRes int button, final Consumer<FeaturedViewModel> function) {
		features.add(new FeaturedViewModel(app, sOrderGenerator.incrementAndGet(), tag, app.getString(title), app.getText(description),
				icon != 0 ? app.getDrawable(icon) : null, button, function, Scopes.app(app).isMarked(SCOPE_TAG_PREFIX_FEATURED + tag)));
	}

	private static final AtomicInteger sOrderGenerator = new AtomicInteger();
}
