package com.oasisfeng.island.model;

import android.app.Activity;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.oasisfeng.android.app.Activities;
import com.oasisfeng.android.databinding.ObservableSortedList;
import com.oasisfeng.android.ui.AppLabelCache;
import com.oasisfeng.island.BR;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

/**
 * View-model for app entry
 *
 * Created by Oasis on 2016/2/9.
 */
public class AppViewModel extends BaseObservable implements ObservableSortedList.Sortable<AppViewModel> {

	public enum State {
		Alive(1),
		Frozen(2),
		Disabled(3),	// System app only
		NotCloned(3),	// Not installed in this user
		Unknown(4);

		State(final int order) { this.order = order; }
		final int order;
	}

	public final String pkg;
	public final CharSequence name;
	public final ObservableField<Drawable> icon = new ObservableField<>();
	public final int flags;				// From ApplicationInfo.flags
	public final boolean launchable;
	public transient final ObservableBoolean exclusive = new ObservableBoolean();	// Only installed & enabled in Island (uninstalled or disabled in owner user)
	public transient final ObservableBoolean selected = new ObservableBoolean(false);
	public final ObservableBoolean auto_freeze = new ObservableBoolean();		// TODO

	public boolean isSystem() { return (flags & FLAG_SYSTEM) != 0; }

	@Bindable public State getState() { return state; }
	void setState(final State state) { this.state = state; notifyPropertyChanged(BR.state); }
	private State state = State.Unknown;		// Setter is provided in AppListViewModel for unique control point

	@SuppressWarnings("unused")		// Used by data binding
	public void onViewAttached(final View v) {
		if (mIconLoading) return;
		final TextView view = (TextView) v;
		final Activity activity = Activities.findActivityFrom(v.getContext());
		if (activity == null) return;
		mIconLoading = true;
		final AppLabelCache cache = AppLabelCache.load(activity);
		cache.loadLabel(this.pkg, new AppLabelCache.LabelLoadCallback() {

			@Override public boolean isCancelled( final String pkg) {
				return false;
			}

			@Override public void onTextLoaded(final String pkg, final CharSequence text, final int flags) {}

			@Override public void onIconLoaded(final String pkg, final Drawable icon) {
				Log.d(TAG, "onIconLoaded for " + pkg);
				AppViewModel.this.icon.set(icon);
			}

			@Override public void onError(final String pkg, final Throwable error) {
				Log.w(TAG, "Error loading label: " + pkg, error);
			}
		});
		// Only show default icon if not cached. If icon is cached, it is already set synchronously in loadLabel().
		if (icon.get() == null) icon.set(activity.getPackageManager().getDefaultActivityIcon());
	}

	public AppViewModel(final String pkg, final CharSequence name, final int flags, final boolean launchable, final boolean exclusive) {
		this.pkg = pkg;
		this.name = name;
		this.flags = flags;
		this.launchable = launchable;
		this.exclusive.set(exclusive);
	}

	@Override public boolean isSameAs(final AppViewModel another) {
		return this == another || pkg.equals(another.pkg);
	}

	@Override public boolean isContentSameAs(final AppViewModel another) {
		return name.equals(another.name) && state == another.state && auto_freeze.get() == another.auto_freeze.get();
	}

	@Override public int compareTo(@NonNull final AppViewModel another) {
		return ORDERING.compare(this, another);
	}

	private String getName() { return name.toString(); }
	private boolean isExclusive() { return exclusive.get(); }

	private final Ordering<AppViewModel> ORDERING = Ordering.natural()
			.onResultOf((Function<AppViewModel, Comparable>) app -> app.getState().order)	// Order by state
			.compound(Ordering.natural().reverse().onResultOf(AppViewModel::isExclusive))	// Exclusive clones first
			.compound(Ordering.natural().onResultOf(AppViewModel::isSystem))				// Non-system apps first
			.compound(Ordering.natural().onResultOf(AppViewModel::getName));				// Order by name

	private volatile boolean mIconLoading;

	private static final String TAG = "AppVM";
}
