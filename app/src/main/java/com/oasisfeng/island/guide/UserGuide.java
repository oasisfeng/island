package com.oasisfeng.island.guide;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActionMenuView;
import android.widget.Toolbar;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.AppListViewModel.Filter;
import com.oasisfeng.island.model.AppViewModel;
import com.oasisfeng.island.util.Users;

import java.util.Collection;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.databinding.BindingAdapter;
import androidx.databinding.ObservableField;
import androidx.lifecycle.LifecycleOwner;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

/**
 * Helper for manipulate user guide menu
 *
 * Created by Oasis on 2017/3/14.
 */
public class UserGuide {

	public final ObservableField<MaterialTapTargetPrompt.Builder> prompt_action = new ObservableField<>();

	public MenuItem.OnMenuItemClickListener getAvailableTip() {
		final Filter primary_filter = mAppListViewModel.mPrimaryFilter.getValue();
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_CLONE) && primary_filter == Filter.Mainland && mAppSelection != null && ! mAppSelection.isSystem())
			return mTipClone;
		if (! mAppScope.isMarked(SCOPE_KEY_TIP_FREEZE) && primary_filter == Filter.Island && mAppSelection != null)
			return mTipFreeze;
		return null;
	}

	private final MenuItem.OnMenuItemClickListener mTipClone = menu -> {
		prompt_action.set(buildPrompt(R.string.prompt_clone_title, R.string.prompt_clone_text).setIcon(R.drawable.ic_add_to_photos_24dp)
				.setPromptStateChangeListener(onHide(SCOPE_KEY_TIP_CLONE)));
		return true;
	};
	private final MenuItem.OnMenuItemClickListener mTipFreeze = menu -> {
		prompt_action.set(buildPrompt(R.string.prompt_freeze_title, R.string.prompt_freeze_text).setIcon(R.drawable.ic_lock_24dp)
				.setPromptStateChangeListener(onHide(SCOPE_KEY_TIP_FREEZE)));
		return true;
	};

	private MaterialTapTargetPrompt.Builder buildPrompt(final @StringRes int title, final @StringRes int text) {
		return new MaterialTapTargetPrompt.Builder(mActivity).setPrimaryText(title).setSecondaryText(text)
				.setCaptureTouchEventOnFocal(true).setCaptureTouchEventOutsidePrompt(true);
	}

	private MaterialTapTargetPrompt.PromptStateChangeListener onHide(final String scope_key) {
		return (prompt, state) -> {
			if (state == MaterialTapTargetPrompt.STATE_FINISHED) {
				mAppScope.markOnly(scope_key);
				mActivity.invalidateOptionsMenu();
			}
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

	public static @Nullable UserGuide initializeIfNeeded(final Activity activity, final LifecycleOwner lifecycle_owner, final AppListViewModel vm) {
		final Scopes.Scope scope = Scopes.app(activity);

		final boolean action_tips_pending = anyActionTipPending(scope);
		if (! action_tips_pending) return null;
		final UserGuide guide = new UserGuide(activity, scope);

		guide.mAppListViewModel = vm;
		vm.mSelection.observe(lifecycle_owner, selection -> guide.mAppSelection = selection);
		final IslandAppListProvider provider = IslandAppListProvider.getInstance(activity);
		provider.registerObserver(new AppListProvider.PackageChangeObserver<IslandAppInfo>() {
			@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
				if (apps.size() != 1) return;		// Batch update is never triggered by user interaction.
				final IslandAppInfo app = apps.iterator().next();
				if (app.isHidden())
					scope.markOnly(SCOPE_KEY_TIP_FREEZE);		// User just froze an app, no need to show tip for app freezing.
				else if (Users.isProfile(app.user) && app.getLastInfo() == null)
					scope.markOnly(SCOPE_KEY_TIP_CLONE);		// User just cloned an app, no need to show tip for app cloning.
				if (scope.isMarked(SCOPE_KEY_TIP_FREEZE) && scope.isMarked(SCOPE_KEY_TIP_CLONE))
					provider.unregisterObserver(this);			// No more interest for package events.
			}

			@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {}
		});
		return guide;
	}

	private static boolean anyActionTipPending(final Scopes.Scope scope) {
		return ! scope.isMarked(SCOPE_KEY_TIP_FREEZE) || ! scope.isMarked(SCOPE_KEY_TIP_CLONE);
	}

	private UserGuide(final Activity activity, final Scopes.Scope scope) { mActivity = activity; mAppScope = scope; }

	private final Activity mActivity;
	private final Scopes.Scope mAppScope;
	private AppViewModel mAppSelection;
	private AppListViewModel mAppListViewModel;

	private static final String SCOPE_KEY_TIP_CLONE = "tip_clone";
	private static final String SCOPE_KEY_TIP_FREEZE = "tip_freeze";
}
