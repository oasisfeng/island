package com.oasisfeng.island.model;

import android.content.Context;

import androidx.annotation.NonNull;

import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.common.app.BaseAppViewModel;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.util.Users;

import java.util.Comparator;

/**
 * View-model for app entry
 *
 * Created by Oasis on 2016/2/9.
 */
public class AppViewModel extends BaseAppViewModel implements ObservableSortedList.Sortable<AppViewModel> {

	public enum State {
		Pending(0),     // Being cloned
		Alive(1),
		Frozen(2),
		Disabled(3),	// System app only
		Uninstalled(4),	// System app only
		Unknown(5);

		State(final int order) { this.order = order; }
		final int order;
	}

	private State checkState() {
		final IslandAppInfo info = info();
		if (! info.isInstalled()) return info.isPlaceHolder() ? State.Pending : State.Uninstalled;
		if (! info.shouldShowAsEnabled()) return State.Disabled;
		if (info.isHidden()) return State.Frozen;
		return State.Alive;
	}

	public CharSequence getStatusText(final Context context) {
		final IslandAppInfo app = info();
		final StringBuilder status = new StringBuilder();
		if (! app.isInstalled()) status.append(context.getString(app.isPlaceHolder() ? R.string.status_pending : R.string.status_uninstalled));
		else if (! app.enabled) status.append(context.getString(R.string.status_disabled));
		else if (app.isHidden()) status.append(context.getString(R.string.status_frozen));
		else status.append(context.getString(R.string.status_alive));
		final boolean exclusive = IslandAppListProvider.getInstance(context).isExclusive(app);
		final StringBuilder appendixes = new StringBuilder();
		if (isSystem()) appendixes.append(", ").append(context.getString(R.string.status_appendix_system));
		if (app.isCritical()) appendixes.append(", ").append(context.getString(R.string.status_appendix_critical));
		if (Users.isOwner(app.user)) {
			if (! exclusive) appendixes.append(", ").append(context.getString(R.string.status_appendix_cloned));
		} else if (exclusive) appendixes.append(", ").append(context.getString(R.string.status_appendix_exclusive));
		if (BuildConfig.DEBUG && app.isSuspended()) appendixes.append(", suspended");     // TODO
		if (appendixes.length() > 0) status.append(" (").append(appendixes.substring(2)).append(')');
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

	@Override public @NonNull String toString() {
		return info().buildToString(AppViewModel.class).append(", state=").append(state).append('}').toString();
	}

	private static final Comparator<AppViewModel> ORDERING = Comparator.<AppViewModel>
			comparingInt(app -> app.state.order)		// Order by state
//			.thenCompare(AppViewModel::isExclusive)		// Exclusive clones first
			.thenComparing(AppViewModel::isSystem)		// Non-system apps first
			.thenComparing(app -> app.info.getLabel());	// Order by label
}
