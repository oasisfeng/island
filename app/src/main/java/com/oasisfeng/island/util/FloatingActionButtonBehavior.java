package com.oasisfeng.island.util;

import android.content.Context;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Expand the default behavior of FloatingActionButton to support {@link BottomSheetBehavior}
 *
 * Created by Oasis on 2016/6/24.
 */
public class FloatingActionButtonBehavior extends FloatingActionButton.Behavior {

	@Override public boolean layoutDependsOn(final CoordinatorLayout parent, final FloatingActionButton child, final View dependency) {
		return super.layoutDependsOn(parent, child, dependency) || isBottomSheet(dependency);
	}

	@Override public boolean onDependentViewChanged(final CoordinatorLayout parent, final FloatingActionButton child, final View dependency) {
		// Block parent behavior for SnackBar if bottom sheet is visible
		if (dependency instanceof Snackbar.SnackbarLayout) {
			final ViewGroup.LayoutParams fab_general_params = child.getLayoutParams();
			if (fab_general_params instanceof CoordinatorLayout.LayoutParams) {
				final CoordinatorLayout.LayoutParams fab_params = ((CoordinatorLayout.LayoutParams) fab_general_params);
				final int anchor_id = fab_params.getAnchorId();
				if (anchor_id != 0) {
					final View anchor = parent.findViewById(anchor_id);
					if (anchor != null && anchor.getVisibility() == View.VISIBLE) return false;
				}
			}
		}
		return super.onDependentViewChanged(parent, child, dependency);
	}

	private boolean isBottomSheet(final View view) {
		final ViewGroup.LayoutParams params = view.getLayoutParams();
		return params instanceof CoordinatorLayout.LayoutParams
				&& ((CoordinatorLayout.LayoutParams) params).getBehavior() instanceof BottomSheetBehavior;
	}

	public FloatingActionButtonBehavior() {}
	public FloatingActionButtonBehavior(final Context context, final AttributeSet attrs) {}
}
