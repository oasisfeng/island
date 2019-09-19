package com.oasisfeng.island.tip;

import android.content.Context;
import android.util.Log;

import com.oasisfeng.ui.card.CardViewModel;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * The interface and controller for all tips.
 *
 * Created by Oasis on 2017/9/7.
 */
public abstract class Tip {

	@WorkerThread protected abstract @Nullable CardViewModel buildCardIfNeeded(final Context context);

	@SuppressWarnings("unchecked") private static final Class<? extends Tip>[] TIPS = new Class[] { CriticalAppRequiredTip.class };

	@WorkerThread public static @Nullable CardViewModel next(final Context context) {
		for (final Class<? extends Tip> tip_class : TIPS) try {
			final Tip tip;
			tip = tip_class.newInstance();
			return tip.buildCardIfNeeded(context);
		} catch (InstantiationException | IllegalAccessException e) {
			Log.e(TAG, "Error initializing tip: " + tip_class.getCanonicalName(), e);
		}
		return null;
	}

	private static final String TAG = "Tip";
}
