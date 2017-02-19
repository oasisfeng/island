package com.oasisfeng.common.databinding;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.databinding.BindingAdapter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.View;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Add elevation change animation when user scrolling the recyclerView.
 *
 * Created by heruoxin on 2016/12/26.
 */
@SuppressWarnings("unused")
public class AnimateElevationAdapter {

    private static final String BROADCAST_SCROLL_STATE_CHANGE = "FloatingElevationRecyclerView:BROADCAST_SCROLL_STATE_CHANGE";
    private static final String NEW_SCROLL_STATE = "FloatingElevationRecyclerView:NEW_SCROLL_STATE";

    @BindingAdapter("elevationChangeOnScroll")
    public static void setAnimator(final View view, final boolean enabled) {
        if (!enabled) return;
        if (view instanceof RecyclerView) {
            registerRecyclerView((RecyclerView) view);
        } else {
            registerAnimationView(view);
        }
    }

    private static void registerRecyclerView(final RecyclerView rv) {
        final OnScrollListener scrollListener = new OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                sendBroadCast(rv, newState);
            }
        };
        rv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                rv.addOnScrollListener(scrollListener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                rv.removeOnScrollListener(scrollListener);
            }
        });
    }

    private static void registerAnimationView(final View view) {
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int scrollState = intent.getIntExtra(NEW_SCROLL_STATE, -1);
                switch (scrollState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                        view.animate().translationZ(0);
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        view.animate().translationZ(view.getElevation());
                        break;
                }
            }
        };
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                IntentFilter filter = new IntentFilter(BROADCAST_SCROLL_STATE_CHANGE + v.getContext().hashCode());
                LocalBroadcastManager.getInstance(v.getContext())
                        .registerReceiver(receiver, filter);

            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                LocalBroadcastManager.getInstance(v.getContext())
                        .unregisterReceiver(receiver);
            }
        });
    }

    private static void sendBroadCast(RecyclerView view, int scrollState) {
        if (view.getContext() instanceof Activity) {
            Intent intent = new Intent(BROADCAST_SCROLL_STATE_CHANGE + view.getContext().hashCode())
                    .putExtra(NEW_SCROLL_STATE, scrollState);
            LocalBroadcastManager.getInstance(view.getContext())
                    .sendBroadcast(intent);
        }
    }

}
