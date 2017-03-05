package com.oasisfeng.common.databinding;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.databinding.BindingAdapter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import com.oasisfeng.island.mobile.R;

/**
 * Binding adapter for alpha src animation of {@link ImageView}.
 *
 * Created by heruoxin on 2016/12/24.
 */
@SuppressWarnings("unused")
public class ImageViewAnimateSrcAdapter {

    @BindingAdapter("animatedSrc")
    public static void setAnimatedSrc(final ImageView view, final Drawable newDrawable) {
        final Drawable oldDrawable = view.getDrawable();
        if (oldDrawable == newDrawable) return;
        Animator anim = (Animator) view.getTag(R.id.image_view_src_animator);
        if (anim != null) anim.cancel();
        final Drawable animatorDrawable;
        ObjectAnimator animator;
        if (oldDrawable == null) {
            newDrawable.setAlpha(0);
            animatorDrawable = newDrawable;
            animator = ObjectAnimator.ofInt(newDrawable, "alpha", 0, 255);
        } else {
            oldDrawable.setAlpha(255);
            if (newDrawable == null) {
                animatorDrawable = oldDrawable;
            } else {
                newDrawable.setAlpha(255);
                animatorDrawable = new LayerDrawable(new Drawable[]{newDrawable, oldDrawable});
            }
            animator = ObjectAnimator.ofInt(oldDrawable, "alpha", 255, 0);
        }
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setImageDrawable(animatorDrawable);
                view.setTag(R.id.image_view_src_animator, animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setImageDrawable(newDrawable);
                view.setTag(R.id.image_view_src_animator, null);
            }

        });
        animator.start();
    }

}

