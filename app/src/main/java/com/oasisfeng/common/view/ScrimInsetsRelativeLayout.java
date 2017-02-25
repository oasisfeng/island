/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.common.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.oasisfeng.island.mobile.R;

/**
 * Copied from android.support.design.internal
 */
public class ScrimInsetsRelativeLayout extends RelativeLayout {

    Drawable mInsetForeground;

    Rect mInsets;

    private Rect mTempRect = new Rect();

    public ScrimInsetsRelativeLayout(Context context) {
        this(context, null);
    }

    public ScrimInsetsRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrimInsetsRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ScrimInsetsFrameLayout, defStyleAttr,
                R.style.Widget_Design_ScrimInsetsFrameLayout);
        mInsetForeground = a.getDrawable(R.styleable.ScrimInsetsFrameLayout_insetForeground);
        a.recycle();
        setWillNotDraw(true); // No need to draw until the insets are adjusted

        ViewCompat.setOnApplyWindowInsetsListener(this,
                (v, insets) -> {
                    if (null == mInsets) {
                        mInsets = new Rect();
                    }
                    mInsets.set(insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom());
                    onInsetsChanged(insets);
                    setWillNotDraw(mInsets.isEmpty() || mInsetForeground == null);
                    ViewCompat.postInvalidateOnAnimation(ScrimInsetsRelativeLayout.this);

                    final int count = getChildCount();

                    for (int i = 0; i < count; i++) {
                        View child = getChildAt(i);
                        if (child.getVisibility() != GONE) {
                            LayoutParams st = (LayoutParams) child.getLayoutParams();
                            if (st.mFitSystemMarginRight) st.rightMargin += mInsets.right;
                            if (st.mFitSystemMarginLeft) st.leftMargin += mInsets.left;
                            if (st.mFitSystemMarginTop) st.topMargin += mInsets.top;
                            if (st.mFitSystemMarginBottom) st.bottomMargin += mInsets.bottom;
                            st.mFitSystemMarginRight = false;
                            st.mFitSystemMarginLeft = false;
                            st.mFitSystemMarginTop = false;
                            st.mFitSystemMarginBottom = false;
                        }
                    }

                    return insets.consumeSystemWindowInsets();
                });
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (mInsets != null && mInsetForeground != null) {
            int sc = canvas.save();
            canvas.translate(getScrollX(), getScrollY());

            // Top
            mTempRect.set(0, 0, width, mInsets.top);
            mInsetForeground.setBounds(mTempRect);
            mInsetForeground.draw(canvas);

            // Bottom
            mTempRect.set(0, height - mInsets.bottom, width, height);
            mInsetForeground.setBounds(mTempRect);
            mInsetForeground.draw(canvas);

            // Left
            mTempRect.set(0, mInsets.top, mInsets.left, height - mInsets.bottom);
            mInsetForeground.setBounds(mTempRect);
            mInsetForeground.draw(canvas);

            // Right
            mTempRect.set(width - mInsets.right, mInsets.top, width, height - mInsets.bottom);
            mInsetForeground.setBounds(mTempRect);
            mInsetForeground.draw(canvas);

            canvas.restoreToCount(sc);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mInsetForeground != null) {
            mInsetForeground.setCallback(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mInsetForeground != null) {
            mInsetForeground.setCallback(null);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    protected void onInsetsChanged(WindowInsetsCompat insets) {
    }

    public static class LayoutParams extends RelativeLayout.LayoutParams {

        public boolean mFitSystemMarginLeft = false;
        public boolean mFitSystemMarginRight = false;
        public boolean mFitSystemMarginTop = false;
        public boolean mFitSystemMarginBottom = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.ScrimInsetsRelativeLayout);
            mFitSystemMarginLeft = a.getBoolean(R.styleable.ScrimInsetsRelativeLayout_fitSystemMarginLeft, false);
            mFitSystemMarginRight = a.getBoolean(R.styleable.ScrimInsetsRelativeLayout_fitSystemMarginRight, false);
            mFitSystemMarginTop = a.getBoolean(R.styleable.ScrimInsetsRelativeLayout_fitSystemMarginTop, false);
            mFitSystemMarginBottom = a.getBoolean(R.styleable.ScrimInsetsRelativeLayout_fitSystemMarginBottom, false);
            a.recycle();
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(RelativeLayout.LayoutParams source) {
            super(source);
        }

    }

}
