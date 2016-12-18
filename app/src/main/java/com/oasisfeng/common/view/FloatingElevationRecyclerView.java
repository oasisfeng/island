package com.oasisfeng.common.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * The elevation of some view(FAB, toolBar) will be higher when recyclerView scrolling.
 *
 * Created by heruoxin on 2016/12/17.
 */
public class FloatingElevationRecyclerView extends RecyclerView {

    public static final String BROADCAST_SCROLL_STATE_CHANGE = "FloatingElevationRecyclerView:BROADCAST_SCROLL_STATE_CHANGE";
    public static final String NEW_SCROLL_STATE = "FloatingElevationRecyclerView:NEW_SCROLL_STATE";

    public FloatingElevationRecyclerView(Context context) {
        super(context);
        init(context);
    }

    public FloatingElevationRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloatingElevationRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                sendBroadCast(newState);
            }
        });
    }

    private void sendBroadCast(int scrollState) {
        if (getContext() instanceof Activity) {
            Intent intent = new Intent(BROADCAST_SCROLL_STATE_CHANGE + getContext().hashCode())
                    .putExtra(NEW_SCROLL_STATE, scrollState);
            LocalBroadcastManager.getInstance(getContext())
                    .sendBroadcast(intent);
        }
    }

}
