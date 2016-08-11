package com.oasisfeng.common.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Information about an installed app, more than {@link ApplicationInfo}.
 *
 * Created by Oasis on 2016/8/5.
 */
public class AppInfo extends ApplicationInfo {

	protected AppInfo(final AppListProvider provider, final ApplicationInfo base, final @Nullable AppInfo last) {
		super(base);
		//noinspection ConstantConditions, context should never be null
		mContext = provider.getContext();
		mLabel = nonLocalizedLabel != null ? nonLocalizedLabel.toString() : provider.getCachedOrTempLabel(this);
		if (last != null) {
			mLastInfo = last;
			last.mLastInfo = null;	// Only store the adjacent last.
		}
	}

	public String getLabel() { return mLabel; }

	/** Is launchable (neither disabled nor hidden) */
	public boolean isLaunchable() { return mIsLaunchable.get(); }

	/** Compare label without loading the string. */
	boolean labelEquals(final AppInfo another) {
		if (nonLocalizedLabel != null)
			return another.nonLocalizedLabel != null && nonLocalizedLabel.toString().equals(another.nonLocalizedLabel.toString());
		if (labelRes != 0)
			return labelRes == another.labelRes;
		if (name != null)
			return name.equals(another.name);
		return packageName.equals(another.packageName);
	}

	public AppInfo getLastInfo() { return mLastInfo; }

	@NonNull protected Context context() { return mContext; }

	interface IconFilter {
		@UiThread Drawable process(Drawable raw_icon);
	}

	interface IconConsumer {
		@UiThread void accept(Drawable icon);
	}

	@UiThread void loadUnbadgedIcon(final @Nullable IconFilter filter, final IconConsumer consumer) {
		loadIcon(filter, consumer, false);
	}

	@UiThread public void loadIcon(final @Nullable IconFilter filter, final IconConsumer consumer) {
		loadIcon(filter, consumer, true);
	}

	@UiThread private void loadIcon(final @Nullable IconFilter filter, final IconConsumer consumer, final boolean need_badge) {
		if (mCachedIcon != null) consumer.accept(mCachedIcon);
		else new AsyncTask<Void, Void, Drawable>() {

			@Override protected Drawable doInBackground(final Void... params) {
				return need_badge ? loadIcon(mContext.getPackageManager()) : loadUnbadgedIconCompat(mContext.getPackageManager());
			}

			@Override protected void onPostExecute(final Drawable drawable) {
				final Drawable icon = (filter != null ? filter.process(drawable) : drawable);
				mCachedIcon = icon;
				consumer.accept(icon);
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	/** Called by {@link AppListProvider#onTrimMemory(int)} to trim memory when UI is hidden */
	@CallSuper protected void trimMemoryOnUiHidden() {
		mCachedIcon = null;
	}

	/** Called by {@link AppListProvider#onTrimMemory(int)} to trim memory in memory-critical situation */
	@CallSuper protected void trimMemoryOnCritical() {
		mCachedIcon = null;
		mLastInfo = null;
		// mLabel is not worth trimming and kept for performance
	}

	private Drawable loadUnbadgedIconCompat(final PackageManager pm) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) return loadUnbadgedIcon(pm);
		Drawable dr = null;
		if (packageName != null) dr = pm.getDrawable(packageName, icon, this);
		if (dr == null) dr = pm.getDefaultActivityIcon();
		return dr;
	}

	private final String mLabel;
	private Drawable mCachedIcon;
	private final @NonNull Context mContext;
	/** The information about the same package before its state is changed to this instance, may not always be kept over time */
	private AppInfo mLastInfo;
	private final Supplier<Boolean> mIsLaunchable = Suppliers.memoize(() -> context().getPackageManager().getLaunchIntentForPackage(packageName) != null);
}
