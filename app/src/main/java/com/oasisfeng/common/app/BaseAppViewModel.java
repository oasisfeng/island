package com.oasisfeng.common.app;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;

import com.oasisfeng.android.ui.IconResizer;
import com.oasisfeng.androidx.lifecycle.NonNullMutableLiveData;
import com.oasisfeng.island.IslandApplication;
import com.oasisfeng.island.mobile.R;

import androidx.databinding.ObservableField;
import androidx.lifecycle.ViewModel;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

/**
 * View-model of basic app entry
 *
 * Created by Oasis on 2016/8/11.
 */
public class BaseAppViewModel extends ViewModel {

	public final AppInfo info;
	public final ObservableField<Drawable> icon = new ObservableField<>();		// Issue in data-binding - MutableLiveData causes initially empty icons.
	public transient final NonNullMutableLiveData<Boolean> selected = new NonNullMutableLiveData<>(false);
	private volatile boolean mIconLoadingStarted;

	public boolean isSystem() { return (info.flags & FLAG_SYSTEM) != 0; }

	@SuppressWarnings("unused")		// Used by data binding
	public void onViewAttached(final View v) {
		if (mIconLoadingStarted) return;
		mIconLoadingStarted = true;
		info.loadUnbadgedIcon(sIconResizer::createIconThumbnail, icon::set);
	}

	public BaseAppViewModel(final AppInfo info) { this.info = info; }

	/* Helper functions for implementing ObservableSortedList.Sortable */

	protected boolean isSameAs(final BaseAppViewModel another) {
		return this == another || info.packageName.equals(another.info.packageName);
	}

	protected boolean isContentSameAs(final BaseAppViewModel another) {
		return TextUtils.equals(info.getLabel(), another.info.getLabel());
	}

	private final static IconResizer sIconResizer = new IconResizer((int) IslandApplication.$().getResources().getDimension(R.dimen.app_icon_size));	// TODO: Avoid static
}
