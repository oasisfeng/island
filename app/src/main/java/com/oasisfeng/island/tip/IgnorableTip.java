package com.oasisfeng.island.tip;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;

import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.ui.card.CardViewModel;

/**
 * Tip with action to be ignored in the future.
 *
 * A ignorable tip should implement other inconspicuous way to remind user about the situation if it persists.
 *
 * Created by Oasis on 2017/9/11.
 */
abstract class IgnorableTip extends Tip {

	@WorkerThread @Override protected @Nullable CardViewModel buildCardIfNeeded(final Context context) {
		return Scopes.app(context).isMarked(mMark) ? null : buildCardIfNotIgnored(context);
	}

	@WorkerThread protected abstract @Nullable CardViewModel buildCardIfNotIgnored(final Context context);

	boolean shouldIgnoreTip(final Context context, final String extra_mark) {
		return Scopes.app(context).isMarked(getMarkWithExtra(extra_mark));
	}

	void ignoreTip(final Context context, final String extra_mark) {
		Scopes.app(context).markOnly(getMarkWithExtra(extra_mark));
	}

	private String getMarkWithExtra(final String extra_mark) {
		return extra_mark != null ? mMark + "#" + extra_mark : mMark;
	}

	IgnorableTip(final String mark) {
		mMark = mark;
	}

	@StringRes static int getIgnoreActionLabel() {
		return R.string.tip_action_ignore;
	}

	private final String mMark;
}
