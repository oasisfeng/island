package com.oasisfeng.island.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SearchView;

import androidx.annotation.Nullable;

/**
 * Do not reset query text upon expanding and collapsing.
 *
 * Created by Oasis on 2019-6-30.
 */
public class PersistableSearchView extends SearchView {

	@Override public void onActionViewExpanded() {
		super.setOnQueryTextListener(null);		// onActionViewExpanded() does not call setQuery()
		super.onActionViewExpanded();
		super.setOnQueryTextListener(mOnQueryTextListener);
	}

	@Override public void onActionViewCollapsed() {
		mBlockSetQuery = true;
		super.onActionViewCollapsed();
		mBlockSetQuery = false;
		if (mOnCloseListener != null) mOnCloseListener.onClose();
	}

	@Override public void setQuery(final CharSequence query, final boolean submit) {
		if (mBlockSetQuery) mBlockSetQuery = false;
		else super.setQuery(query, submit);
	}

	@Override public void setOnCloseListener(final OnCloseListener listener) { super.setOnCloseListener(mOnCloseListener = listener); }
	@Override public void setOnQueryTextListener(final OnQueryTextListener listener) { super.setOnQueryTextListener(mOnQueryTextListener = listener); }

	public PersistableSearchView(final Context context) { super(context); }
	public PersistableSearchView(final Context context, final AttributeSet attrs) { super(context, attrs); }
	public PersistableSearchView(final Context context, final AttributeSet attrs, final int defStyleAttr) { super(context, attrs, defStyleAttr); }
	@SuppressWarnings("unused") public PersistableSearchView(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	private boolean mBlockSetQuery;
	private @Nullable OnCloseListener mOnCloseListener;
	private @Nullable OnQueryTextListener mOnQueryTextListener;
}
