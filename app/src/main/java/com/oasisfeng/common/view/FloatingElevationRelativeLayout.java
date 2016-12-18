package com.oasisfeng.common.view;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Px;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * The elevation of some view(FAB, toolBar) will be higher when recyclerView scrolling
 *
 * Created by heruoxin on 2016/12/17.
 */
public class FloatingElevationRelativeLayout extends RelativeLayout {

    private BroadcastReceiver mReceiver;
    private float mElevation;

    public FloatingElevationRelativeLayout(Context context) {
        super(context);
        init(context);
    }

    public FloatingElevationRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloatingElevationRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public FloatingElevationRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int scrollState = intent.getIntExtra(FloatingElevationRecyclerView.NEW_SCROLL_STATE, -1);
                switch (scrollState) {
                    case FloatingElevationRecyclerView.SCROLL_STATE_IDLE:
                        animate().translationZ(0);
                        break;
                    case FloatingElevationRecyclerView.SCROLL_STATE_DRAGGING:
                        animate().translationZ(mElevation);
                        break;
                }
            }
        };
    }

    @Override
    public void setElevation(@SuppressLint("SupportAnnotationUsage") @Px float elevation) {
        mElevation = elevation;
        super.setElevation(elevation);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(mReceiver, new IntentFilter(FloatingElevationRecyclerView.BROADCAST_SCROLL_STATE_CHANGE + getContext().hashCode()));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(mReceiver);
    }

}
