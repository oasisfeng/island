package com.oasisfeng.common.databinding;

import android.animation.ValueAnimator;
import android.databinding.BindingAdapter;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.ColorInt;
import android.view.View;

import com.oasisfeng.common.util.ColorUtil;
import com.oasisfeng.island.mobile.R;

/**
 * Animation data binding adapter.
 *
 * Created by heruoxin on 2017/1/14.
 */
@SuppressWarnings("unused")
public class AnimatedBindingAdapter {

    private static final long DURATION_SHORT = 200;

    @BindingAdapter("animatedElevation")
    public static void bindAnimatedElevation(final View view, final float elevation) {
        view.animate().setDuration(DURATION_SHORT).translationZ(elevation);
    }

    @BindingAdapter("animatedScaleX")
    public static void bindAnimatedScaleX(final View view, final float x) {
        view.animate().setDuration(DURATION_SHORT).scaleX(x);
    }

    @BindingAdapter("animatedScaleY")
    public static void bindAnimatedScaleY(final View view, final float y) {
        view.animate().setDuration(DURATION_SHORT).scaleY(y);
    }

    @BindingAdapter("animatedBackgroundColor")
    public static void bindAnimatedBackgroundColor(final View view, final @ColorInt int colorTo) {
        if (view.getBackground() == null || !(view.getBackground() instanceof ColorDrawable)) {
            view.setBackgroundColor(colorTo);
            return;
        }
        final @ColorInt int colorNow = ((ColorDrawable) view.getBackground()).getColor();
        if (colorNow == colorTo) return;
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(va -> {
            float fraction = va.getAnimatedFraction();
            int interpolateColor = ColorUtil.interpolateColorHsv(colorNow, colorTo, fraction);
            view.setBackgroundColor(interpolateColor);
        });
        ValueAnimator previousAnimator = (ValueAnimator) view.getTag(R.id.view_background_animator);
        if (previousAnimator != null) previousAnimator.cancel();
        animator.setDuration(DURATION_SHORT).start();
        view.setTag(R.id.view_background_animator, animator);
    }

}
