package com.oasisfeng.island.guide;

import android.app.Activity;
import android.databinding.BindingAdapter;
import android.databinding.Observable;
import android.databinding.ObservableField;
import android.support.annotation.StringRes;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActionMenuView;
import android.widget.Toolbar;

import com.android.databinding.library.baseAdapters.BR;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppListViewModel.Filter;

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
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_CLONE) && mAppSelected && mFilter == Filter.Mainland) return mTipClone;
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_FREEZE) && mAppSelected && mFilter == Filter.Island) return mTipFreeze;
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

	@BindingAdapter("prompt") @SuppressWarnings("unused")
	public static void setPrompt(final View view, final MaterialTapTargetPrompt.Builder prompt) {
		final Activity activity = Activities.findActivityFrom(view.getContext());
		if (activity == null) return;
		if (prompt == view.getTag(R.id.prompt)) return;	// Invocation de-dup
		view.setTag(R.id.prompt, prompt);
		if (prompt == null) return;
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

	public UserGuide(final Activity activity, final AppListViewModel vm) {
		mActivity = activity;
		mAppScope = Scopes.app(activity);
		vm.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
			@Override public void onPropertyChanged(final Observable sender, final int property) {
				if (property == BR.filterPrimaryChoice) {
					final AppListViewModel apps_vm = ((AppListViewModel) sender);
					mFilter = apps_vm.getFilterPrimaryOptions().get(apps_vm.getFilterPrimaryChoice()).parent();
				} else if (property == BR.selection) {
					final AppListViewModel apps_vm = ((AppListViewModel) sender);
					mAppSelected = apps_vm.getSelection() != null;
				}
			}
		});	// removeOnPropertyChangedCallback() is never called since this class shares the same life-cycle with AppListViewModel.
	}

	private final Activity mActivity;
	private final Scopes.Scope mAppScope;
	private Filter mFilter;
	private boolean mAppSelected;

	private static final String SCOPE_KEY_TIP_FILTER = "tip_filter";
	private static final String SCOPE_KEY_TIP_CLONE = "tip_clone";
	private static final String SCOPE_KEY_TIP_FREEZE = "tip_freeze";
}
