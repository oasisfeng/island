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

package com.android.packageinstaller.permission.ui.handheld;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.permission.ui.ButtonBarLayout;
import com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler;
import com.android.packageinstaller.permission.ui.ManualLayoutFrame;
import com.oasisfeng.island.permission.R;

import static android.os.Build.VERSION_CODES.M;

@RequiresApi(M)
public final class GrantPermissionsViewHandlerImpl
        implements GrantPermissionsViewHandler, OnClickListener {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    public static final String ARG_GROUP_SHOW_DO_NOT_ASK = "ARG_GROUP_SHOW_DO_NOT_ASK";
    public static final String ARG_GROUP_DO_NOT_ASK_CHECKED = "ARG_GROUP_DO_NOT_ASK_CHECKED";

    // Animation parameters.
    private static final long OUT_DURATION = 200;
    private static final long IN_DURATION = 300;

    private final Context mContext;

    private ResultListener mResultListener;

    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private boolean mShowDonNotAsk;
    private boolean mDoNotAskChecked;

    private ImageView mIconView;
    private TextView mCurrentGroupView;
    private TextView mMessageView;
    private CheckBox mDoNotAskCheckbox;
    private Button mAllowButton;

    private ManualLayoutFrame mRootView;

    // Needed for animation
    private ViewGroup mDescContainer;
    private ViewGroup mCurrentDesc;
    private ViewGroup mDialogContainer;
    private ButtonBarLayout mButtonBar;

    public GrantPermissionsViewHandlerImpl(Context context) {
        mContext = context;
    }

    @Override
    public GrantPermissionsViewHandlerImpl setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public void saveInstanceState(Bundle arguments) {
        arguments.putString(ARG_GROUP_NAME, mGroupName);
        arguments.putInt(ARG_GROUP_COUNT, mGroupCount);
        arguments.putInt(ARG_GROUP_INDEX, mGroupIndex);
        arguments.putParcelable(ARG_GROUP_ICON, mGroupIcon);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, mGroupMessage);
        arguments.putBoolean(ARG_GROUP_SHOW_DO_NOT_ASK, mShowDonNotAsk);
        arguments.putBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED, mDoNotAskCheckbox.isChecked());
    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mShowDonNotAsk = savedInstanceState.getBoolean(ARG_GROUP_SHOW_DO_NOT_ASK);
        mDoNotAskChecked = savedInstanceState.getBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED);
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, boolean showDonNotAsk) {
        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mShowDonNotAsk = showDonNotAsk;
        mDoNotAskChecked = false;
        // If this is a second (or later) permission and the views exist, then animate.
        if (mIconView != null) {
            if (mGroupIndex > 0) {
                // The first message will be announced as the title of the activity, all others
                // we need to announce ourselves.
                mDescContainer.announceForAccessibility(message);
                animateToPermission();
            } else {
                updateDescription();
                updateGroup();
                updateDoNotAskCheckBox();
            }
        }
    }

    public void onConfigurationChanged() {
        mRootView.onConfigurationChanged();
    }

    private void animateOldContent(Runnable callback) {
        // Fade out old description group and scale out the icon for it.
        Interpolator interpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_linear_in);

        // Icon scale to zero
        mIconView.animate()
                .scaleX(0)
                .scaleY(0)
                .setDuration(OUT_DURATION)
                .setInterpolator(interpolator)
                .start();

        // Description fade out
        mCurrentDesc.animate()
                .alpha(0)
                .setDuration(OUT_DURATION)
                .setInterpolator(interpolator)
                .withEndAction(callback)
                .start();

        // Checkbox fade out if needed
        if (!mShowDonNotAsk && mDoNotAskCheckbox.getVisibility() == View.VISIBLE) {
            mDoNotAskCheckbox.animate()
                    .alpha(0)
                    .setDuration(OUT_DURATION)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    private void attachNewContent(final Runnable callback) {
        mCurrentDesc = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.permission_description, mDescContainer, false);
        mDescContainer.removeAllViews();
        mDescContainer.addView(mCurrentDesc);

        mDialogContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    mDialogContainer.removeOnLayoutChangeListener(this);

                    // Prepare new content to the right to be moved in
                    final int containerWidth = mDescContainer.getWidth();
                    mCurrentDesc.setTranslationX(containerWidth);

                    // How much scale for the dialog to appear the same?
                    final int oldDynamicHeight = oldBottom - oldTop - mButtonBar.getHeight();
                    final float scaleY = (float) oldDynamicHeight / mDescContainer.getHeight();

                    // How much to translate for the dialog to appear the same?
                    final int translationCompensatingScale = (int) (scaleY
                            * mDescContainer.getHeight() - mDescContainer.getHeight()) / 2;
                    final int translationY = (oldTop - top) + translationCompensatingScale;

                    // Animate to the current layout
                    mDescContainer.setScaleY(scaleY);
                    mDescContainer.setTranslationY(translationY);
                    mDescContainer.animate()
                            .translationY(0)
                            .scaleY(1.0f)
                            .setInterpolator(AnimationUtils.loadInterpolator(mContext,
                                    android.R.interpolator.linear_out_slow_in))
                            .setDuration(IN_DURATION)
                            .withEndAction(callback)
                            .start();
                }
            }
        );

        mMessageView = (TextView) mCurrentDesc.findViewById(R.id.permission_message);
        mIconView = (ImageView) mCurrentDesc.findViewById(R.id.permission_icon);

        final boolean doNotAskWasShown = mDoNotAskCheckbox.getVisibility() == View.VISIBLE;

        updateDescription();
        updateGroup();
        updateDoNotAskCheckBox();

        if (!doNotAskWasShown && mShowDonNotAsk) {
            mDoNotAskCheckbox.setAlpha(0);
        }
    }

    private void animateNewContent() {
        Interpolator interpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.linear_out_slow_in);

        // Description slide in
        mCurrentDesc.animate()
                .translationX(0)
                .setDuration(IN_DURATION)
                .setInterpolator(interpolator)
                .start();

        // Checkbox fade in if needed
        if (mShowDonNotAsk && mDoNotAskCheckbox.getVisibility() == View.VISIBLE
                && mDoNotAskCheckbox.getAlpha() < 1.0f) {
            mDoNotAskCheckbox.setAlpha(0);
            mDoNotAskCheckbox.animate()
                    .alpha(1.0f)
                    .setDuration(IN_DURATION)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    private void animateToPermission() {
        // Remove the old content
        animateOldContent(new Runnable() {
            @Override
            public void run() {
                // Add the new content
                attachNewContent(new Runnable() {
                    @Override
                    public void run() {
                        // Animate the new content
                        animateNewContent();
                    }
                });
            }
        });
    }

    @Override
    public View createView() {
        mRootView = (ManualLayoutFrame) LayoutInflater.from(mContext)
                .inflate(R.layout.grant_permissions, null);
        mButtonBar = (ButtonBarLayout) mRootView.findViewById(R.id.button_group);
        mButtonBar.setAllowStacking(true);
        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mCurrentGroupView = (TextView) mRootView.findViewById(R.id.current_page_text);
        mDoNotAskCheckbox = (CheckBox) mRootView.findViewById(R.id.do_not_ask_checkbox);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);

        mDialogContainer = (ViewGroup) mRootView.findViewById(R.id.dialog_container);
        mDescContainer = (ViewGroup) mRootView.findViewById(R.id.desc_container);
        mCurrentDesc = (ViewGroup) mRootView.findViewById(R.id.perm_desc_root);

        mAllowButton.setOnClickListener(this);
        mRootView.findViewById(R.id.permission_deny_button).setOnClickListener(this);
        mDoNotAskCheckbox.setOnClickListener(this);

        if (mGroupName != null) {
            updateDescription();
            updateGroup();
            updateDoNotAskCheckBox();
        }

        return mRootView;
    }

    @Override
    public void updateWindowAttributes(LayoutParams outLayoutParams) {
        // No-op
    }

    private void updateDescription() {
        mIconView.setImageDrawable(mGroupIcon.loadDrawable(mContext));
        mMessageView.setText(mGroupMessage);
    }

    private void updateGroup() {
        if (mGroupCount > 1) {
            mCurrentGroupView.setVisibility(View.VISIBLE);
            mCurrentGroupView.setText(mContext.getString(R.string.current_permission_template,
                    mGroupIndex + 1, mGroupCount));
        } else {
            mCurrentGroupView.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDoNotAskCheckBox() {
        if (mShowDonNotAsk) {
            mDoNotAskCheckbox.setVisibility(View.VISIBLE);
            mDoNotAskCheckbox.setOnClickListener(this);
            mDoNotAskCheckbox.setChecked(mDoNotAskChecked);
        } else {
            mDoNotAskCheckbox.setVisibility(View.GONE);
            mDoNotAskCheckbox.setOnClickListener(null);
        }
    }

    @Override
    public void onClick(View view) {
        final int id = view.getId();
        if (id == R.id.permission_allow_button) {
            if (mResultListener != null) {
//                view.clearAccessibilityFocus();
                mResultListener.onPermissionGrantResult(mGroupName, true, false);
            }
        } else if (id == R.id.permission_deny_button) {
            mAllowButton.setEnabled(true);
            if (mResultListener != null) {
//                view.clearAccessibilityFocus();
                mResultListener.onPermissionGrantResult(mGroupName, false,
                        mDoNotAskCheckbox.isChecked());
            }
        } else if (id == R.id.do_not_ask_checkbox) {
            mAllowButton.setEnabled(! mDoNotAskCheckbox.isChecked());
        }
    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            final boolean doNotAskAgain = mDoNotAskCheckbox.isChecked();
            mResultListener.onPermissionGrantResult(mGroupName, false, doNotAskAgain);
        }
    }
}
