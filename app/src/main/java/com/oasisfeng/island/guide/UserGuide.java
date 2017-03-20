package com.oasisfeng.island.guide;

import android.app.Activity;
import android.databinding.BindingAdapter;
import android.databinding.Observable;
import android.databinding.ObservableField;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActionMenuView;
import android.widget.Toolbar;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppListViewModel.Filter;
import com.oasisfeng.island.model.AppViewModel;
import com.oasisfeng.island.util.Users;

import java.util.Collection;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

/**
 * Helper for manipulate user guide menu
 *
 * Created by Oasis on 2017/3/14.
 */
public class UserGuide {

	public final ObservableField<MaterialTapTargetPrompt.Builder> prompt_filter = new ObservableField<>();
	public final ObservableField<MaterialTapTargetPrompt.Builder> prompt_action = new ObservableField<>();

	public MenuItem.OnMenuItemClickListener getAvailableTip() {
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_FILTER)) return mTipFilter;
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_CLONE) && mFilter == Filter.Mainland && mAppSelection != null && ! mAppSelection.isSystem()) return mTipClone;
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_FREEZE) && mFilter == Filter.Island && mAppSelection != null) return mTipFreeze;
		return null;
	}

	private final MenuItem.OnMenuItemClickListener mTipFilter = menu -> {
		prompt_filter.set(buildPrompt(R.string.prompt_filter_title, R.string.prompt_filter_text).setOnHidePromptListener(onHide(SCOPE_KEY_TIP_FILTER)));
		return true;
	};
	private final MenuItem.OnMenuItemClickListener mTipClone = menu -> {
		prompt_action.set(buildPrompt(R.string.prompt_clone_title, R.string.prompt_clone_text).setIcon(R.drawable.ic_add_to_photos_24dp)
				.setOnHidePromptListener(onHide(SCOPE_KEY_TIP_CLONE)));
		return true;
	};
	private final MenuItem.OnMenuItemClickListener mTipFreeze = menu -> {
		prompt_action.set(buildPrompt(R.string.prompt_freeze_title, R.string.prompt_freeze_text).setIcon(R.drawable.ic_lock_24dp)
				.setOnHidePromptListener(onHide(SCOPE_KEY_TIP_FREEZE)));
		return true;
	};

	private MaterialTapTargetPrompt.Builder buildPrompt(final @StringRes int title, final @StringRes int text) {
		return new MaterialTapTargetPrompt.Builder(mActivity).setPrimaryText(title).setSecondaryText(text)
				.setCaptureTouchEventOnFocal(true).setCaptureTouchEventOutsidePrompt(true);
	}

	private MaterialTapTargetPrompt.OnHidePromptListener onHide(final String scope_key) {
		return new MaterialTapTargetPrompt.OnHidePromptListener() {
			@Override public void onHidePrompt(final MotionEvent event, final boolean tappedTarget) {}
			@Override public void onHidePromptComplete() { mAppScope.mark(scope_key); mActivity.invalidateOptionsMenu(); }
		};
	}

	@BindingAdapter("android:prompt") @SuppressWarnings("unused")
	public static void setPrompt(final View view, final MaterialTapTargetPrompt.Builder prompt) {
		if (prompt == null) return;
		if (prompt == view.getTag(R.id.prompt)) return;		// Invocation de-dup
		view.setTag(R.id.prompt, prompt);

		prompt.setTarget(findProperTarget(view)).show();
	}

	private static View findProperTarget(final View view) {
		if (! (view instanceof Toolbar)) return view;
		final ViewGroup group = (ViewGroup) view;
		for (int i = 0; i < group.getChildCount(); i ++) {
			final View child = group.getChildAt(i);
			if (child instanceof ActionMenuView) return ((ActionMenuView) child).getChildAt(0);	// ActionMenuItemView
		}
		return view;
	}

	public static @Nullable UserGuide initializeIfNeeded(final Activity activity, final AppListViewModel vm) {
		final Scopes.Scope scope = Scopes.app(activity);

		final boolean action_tips_pending = UserGuide.anyActionTipPending(scope);
		if (! action_tips_pending && scope.isMarked(SCOPE_KEY_TIP_FILTER)) return null;
		final UserGuide guide = new UserGuide(activity, scope);

		vm.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() { @Override public void onPropertyChanged(final Observable sender, final int property) {
			if (property == BR.filterPrimaryChoice) {
				final AppListViewModel vm = (AppListViewModel) sender;
				final Filter filter = vm.getFilterPrimaryOptions().get(vm.getFilterPrimaryChoice()).parent();
				if (guide.mFilter != null && filter != guide.mFilter) {
					scope.mark(SCOPE_KEY_TIP_FILTER);				// User just switched filter, no need to show tip for filter switching.
					activity.invalidateOptionsMenu();
					if (! UserGuide.anyActionTipPending(scope))
						vm.removeOnPropertyChangedCallback(this);	// No need to monitor filter or selection any more.
				}
				guide.mFilter = filter;
			} else if (property == BR.selection) guide.mAppSelection = ((AppListViewModel) sender).getSelection();
		}});
		if (action_tips_pending) {
			final IslandAppListProvider provider = IslandAppListProvider.getInstance(activity);
			provider.registerObserver(new AppListProvider.PackageChangeObserver<IslandAppInfo>() {
				@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
					if (apps.size() != 1) return;		// Batch update is never triggered by user interaction.
					final IslandAppInfo app = apps.iterator().next();
					if (app.isHidden())
						scope.mark(SCOPE_KEY_TIP_FREEZE);			// User just froze an app, no need to show tip for app freezing.
					else if (Users.isProfile(app.user) && app.getLastInfo() == null)
						scope.mark(SCOPE_KEY_TIP_CLONE);			// User just cloned an app, no need to show tip for app cloning.
					if (scope.isMarked(SCOPE_KEY_TIP_FREEZE) && scope.isMarked(SCOPE_KEY_TIP_CLONE))
						provider.unregisterObserver(this);			// No more interest for package events.
				}

				@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {}
			});
		}
		return guide;
	}

	private static boolean anyActionTipPending(final Scopes.Scope scope) {
		return ! scope.isMarked(SCOPE_KEY_TIP_FREEZE) || ! scope.isMarked(SCOPE_KEY_TIP_CLONE);
	}

	private UserGuide(final Activity activity, final Scopes.Scope scope) { mActivity = activity; mAppScope = scope; }

	private final Activity mActivity;
	private final Scopes.Scope mAppScope;
	private Filter mFilter;
	private AppViewModel mAppSelection;

	private static final String SCOPE_KEY_TIP_FILTER = "tip_filter";
	private static final String SCOPE_KEY_TIP_CLONE = "tip_clone";
	private static final String SCOPE_KEY_TIP_FREEZE = "tip_freeze";
}
