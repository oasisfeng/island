package com.oasisfeng.island.model;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Ordering;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.common.app.BaseAppViewModel;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.util.Users;

import java.util.Arrays;

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
		Unknown(4);

		State(final int order) { this.order = order; }
		final int order;
	}

	private State checkState() {
		if (! info().shouldShowAsEnabled()) return State.Disabled;
		if (info().isHidden()) return State.Frozen;
		return State.Alive;
	}

	public CharSequence getStatusText(final Context context) {
		final StringBuilder status = new StringBuilder();
		if (! info().enabled) status.append(context.getString(R.string.status_disabled));
		else if (info().isHidden()) status.append(context.getString(R.string.status_frozen));
		else status.append(context.getString(R.string.status_alive));
		final boolean is_system = isSystem();
		final boolean exclusive = IslandAppListProvider.getInstance(context).isExclusive(info());
		final String appendixes = Joiner.on(", ").skipNulls().join(Arrays.asList(
				is_system ? context.getString(R.string.status_appendix_system) : null,
				Users.isOwner(info().user) ? (exclusive ? null : context.getString(R.string.status_appendix_cloned))
						: (exclusive ? context.getString(R.string.status_appendix_exclusive) : null)
		));
		if (! appendixes.isEmpty()) status.append(" (").append(appendixes).append(')');
		return status;
	}

	public IslandAppInfo info() { return (IslandAppInfo) info; }

	public String getDebugInfo() {
		return "NULL";
	}

	public final State state;

	AppViewModel(final IslandAppInfo info) {
		super(info);
		state = checkState();
	}

	@Override public boolean isSameAs(final AppViewModel another) {
		return super.isSameAs(another);
	}

	@Override public boolean isContentSameAs(final AppViewModel another) {
		return super.isContentSameAs(another) && state == another.state;
	}

	@Override public int compareTo(@NonNull final AppViewModel another) {
		return ORDERING.compare(this, another);
	}

	@Override public String toString() {
		return info().fillToString(MoreObjects.toStringHelper(AppViewModel.class)).add("state", state).toString();
	}

	private static final Ordering<AppViewModel> ORDERING = Ordering.natural()
			.onResultOf((Function<AppViewModel, Comparable>) app -> app.state.order)		// Order by state
//			.compound(Ordering.natural().reverse().onResultOf(AppViewModel::isExclusive))	// Exclusive clones first
			.compound(Ordering.natural().onResultOf(AppViewModel::isSystem))				// Non-system apps first
			.compound(Ordering.natural().onResultOf(app -> app.info.getLabel()));			// Order by label
}
