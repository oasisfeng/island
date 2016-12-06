package com.oasisfeng.island.model;

import android.content.Context;
import android.databinding.ObservableBoolean;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.common.app.BaseAppViewModel;
import com.oasisfeng.island.R;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.util.Users;

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
		NotCloned(3),	// Not installed in Island
		Unknown(4);

		State(final int order) { this.order = order; }
		final int order;
	}

	private State checkState() {
		if (Users.isOwner(info().user)) return State.NotCloned;	// FIXME
//		if (! info().isInstalledInIsland()) return State.NotCloned;
		if (! info.enabled) return State.Disabled;
		if (info().isHidden()) return State.Frozen;
		return State.Alive;
	}

	public @StringRes int getStatusText() {
// android:_text="@{(apps.selection.state == State.Alive ? @string/state_alive : apps.selection.state == State.Frozen ? @string/state_frozen : apps.selection.state == State.Disabled ? @string/state_disabled : apps.selection.state == State.NotCloned ? @string/state_not_cloned : null) + &quot; &quot; + ((apps.selection.state == State.Alive || apps.selection.state == State.Frozen) &amp;&amp; apps.selection.isExclusive() ? @string/status_exclusive : &quot;&quot;)}"
		if (! info().enabled) return R.string.state_disabled;
		if (info().isHidden()) return R.string.state_frozen;
		if (! info().isInstalled()) return R.string.state_not_cloned;
		return R.string.state_alive;
	}

	public boolean isExclusive(final Context context) { return ! Apps.of(context).isInstalledInCurrentUser(info.packageName); }

	public IslandAppInfo info() { return (IslandAppInfo) info; }

	public final State state;
	private final ObservableBoolean auto_freeze = new ObservableBoolean();		// TODO

	AppViewModel(final IslandAppInfo info) {
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

	private static final Ordering<AppViewModel> ORDERING = Ordering.natural()
			.onResultOf((Function<AppViewModel, Comparable>) app -> app.state.order)		// Order by state
//			.compound(Ordering.natural().reverse().onResultOf(AppViewModel::isExclusive))	// Exclusive clones first
			.compound(Ordering.natural().onResultOf(AppViewModel::isSystem))				// Non-system apps first
			.compound(Ordering.natural().onResultOf(app -> app.info.getLabel()));			// Order by label
}
