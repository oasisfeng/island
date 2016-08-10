package com.oasisfeng.island.model;

import android.databinding.ObservableBoolean;
import android.support.annotation.NonNull;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.common.app.BaseAppViewModel;
import com.oasisfeng.island.data.IslandAppInfo;

/**
 * View-model for app entry
 *
 * Created by Oasis on 2016/2/9.
 */
public class AppViewModel extends BaseAppViewModel implements ObservableSortedList.Sortable<AppViewModel> {

	public enum State {
		Alive(1),
		Frozen(2),
		Disabled(3),	// System app only
		NotCloned(3),	// Not installed in this user
		Unknown(4);

		State(final int order) { this.order = order; }
		final int order;
	}

	private State checkState() {
		if (! info().isInstalledInUser()) return State.NotCloned;
		if (! info.enabled) return State.Disabled;
		if (info().isHiddenOrNotInstalled()) return State.Frozen;
		return State.Alive;
	}

	private IslandAppInfo info() { return (IslandAppInfo) info; }

	public final State state;
	private final ObservableBoolean auto_freeze = new ObservableBoolean();		// TODO

	public AppViewModel(final IslandAppInfo info) {
		super(info);
		this.state = checkState();
	}

	@Override public boolean isSameAs(final AppViewModel another) {
		return super.isSameAs(another);
	}

	@Override public boolean isContentSameAs(final AppViewModel another) {
		return super.isContentSameAs(another) && state == another.state && auto_freeze.get() == another.auto_freeze.get();
	}

	@Override public int compareTo(@NonNull final AppViewModel another) {
		return ORDERING.compare(this, another);
	}

	public boolean isExclusive() { return ! ((IslandAppInfo) info).checkInstalledInOwner(); }

	private final Ordering<AppViewModel> ORDERING = Ordering.natural()
			.onResultOf((Function<AppViewModel, Comparable>) app -> app.state.order)		// Order by state
			.compound(Ordering.natural().reverse().onResultOf(AppViewModel::isExclusive))	// Exclusive clones first
			.compound(Ordering.natural().onResultOf(AppViewModel::isSystem))				// Non-system apps first
			.compound(Ordering.natural().onResultOf(app -> app.info.getLabel()));			// Order by label
}
