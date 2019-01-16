package com.oasisfeng.island.featured;

import android.app.Application;
import android.graphics.drawable.Drawable;

import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.util.Consumer;
import com.oasisfeng.androidx.lifecycle.NonNullMutableLiveData;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;

/**
 * Created by Oasis on 2018/5/18.
 */
public class FeaturedViewModel extends AndroidViewModel implements ObservableSortedList.Sortable<FeaturedViewModel> {

	public final String tag;
	public final String title;
	public final CharSequence description;
	public final @Nullable Drawable icon;
	public final NonNullMutableLiveData<Integer> button;
	public final @Nullable Consumer<FeaturedViewModel> function;
	public final NonNullMutableLiveData<Boolean> dismissed;

	@Override public boolean isSameAs(final FeaturedViewModel another) {
		return tag.equals(another.tag);
	}

	@Override public boolean isContentSameAs(final FeaturedViewModel o) {
		return Objects.equals(title, o.title) && Objects.equals(description, o.description) && Objects.equals(button, o.button);
	}

	@Override public int compareTo(@NonNull final FeaturedViewModel o) {
		int result = dismissed.getValue().compareTo(o.dismissed.getValue());
		if (result == 0) result = Integer.compare(order, o.order);
		return result;
	}

	FeaturedViewModel(final Application app, final int order, final String tag, final String title, final CharSequence description,
			final @Nullable Drawable icon, final @StringRes int button, final @Nullable Consumer<FeaturedViewModel> function, final boolean dismissed) {
		super(app);
		this.order = order;
		this.tag = tag;
		this.title = title;
		this.description = description;
		this.icon = icon;
		this.button = new NonNullMutableLiveData<>(button);
		this.function = function;
		this.dismissed = new NonNullMutableLiveData<>(dismissed);
	}

	private final int order;
}
