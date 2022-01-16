package com.oasisfeng.common.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.function.Supplier;

import static android.content.Context.LAUNCHER_APPS_SERVICE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Information about an installed app, more than {@link ApplicationInfo}.
 *
 * Created by Oasis on 2016/8/5.
 */
public abstract class AppInfo extends ApplicationInfo {

	protected static final int PRIVATE_FLAG_HIDDEN = 1;

	protected AppInfo(final AppListProvider<? extends AppInfo> provider, final ApplicationInfo base,
	                  final @Nullable AppInfo last, final @Nullable CharSequence label) {
		super(base);
		mProvider = provider;
		mLabel = label != null ? label.toString() : nonLocalizedLabel != null ? nonLocalizedLabel.toString()
				: provider.getCachedOrTempLabel(this);
		if (last != null) {
			mLastInfo = last;
			last.mLastInfo = null;	// Only store the adjacent last.
			if (TextUtils.equals(sourceDir, last.sourceDir)) mCachedIcon = last.mCachedIcon;    // Reuse icon if package source-dir is unchanged.
		}
	}

	protected abstract AppInfo cloneWithLabel(final CharSequence label);

	public String getLabel() { return mLabel; }

	public boolean isPlaceHolder() { return ! isSystem() && ! isInstalled(); }
	public boolean isInstalled() { return (flags & ApplicationInfo.FLAG_INSTALLED) != 0; }
	public boolean isSystem() { return (flags & ApplicationInfo.FLAG_SYSTEM) != 0; }
	public boolean isSuspended() { return (flags & ApplicationInfo.FLAG_SUSPENDED) != 0; }

	public boolean isHidden() {
		final Boolean hidden = isHidden(this);
		if (hidden != null) return hidden;
		// The fallback implementation
		return ! requireNonNull((LauncherApps) context().getSystemService(LAUNCHER_APPS_SERVICE)).isPackageEnabled(packageName, Users.current());
	}

	/** @return hidden state, or null if failed to */
	private static @Nullable Boolean isHidden(final ApplicationInfo info) {
		final Integer private_flags = Hacks.ApplicationInfo_privateFlags.get(info);
		return private_flags != null ? (private_flags & PRIVATE_FLAG_HIDDEN) != 0 : null;
	}

	/** Is launchable (and neither disabled nor hidden) */
	public boolean isLaunchable() { return mIsLaunchable.get(); }
	private final Supplier<Boolean> mIsLaunchable = lazyLessMutable(() -> checkLaunchable(0));

	protected boolean checkLaunchable(final int flags_for_resolve) {
		final Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName);
		final ResolveInfo resolved = context().getPackageManager().resolveActivity(intent, flags_for_resolve);
		return resolved != null;
	}

	public AppInfo getLastInfo() { return mLastInfo; }

	interface IconFilter { @UiThread Drawable process(Drawable raw_icon); }
	interface IconConsumer { @UiThread void accept(Drawable icon); }

	@UiThread void loadUnbadgedIcon(final @Nullable IconFilter filter, final IconConsumer consumer) {
		loadIcon(filter, consumer, false);
	}

	@UiThread public void loadIcon(final @Nullable IconFilter filter, final IconConsumer consumer) {
		loadIcon(filter, consumer, true);
	}

	@SuppressLint("StaticFieldLeak")	// The outer class has no direct reference to Context
	@UiThread private void loadIcon(final @Nullable IconFilter filter, final IconConsumer consumer, final boolean need_badge) {
		if (mCachedIcon == null) try {
			new AsyncTask<Void, Void, Drawable>() {

				@Override protected Drawable doInBackground(final Void... params) {
					return need_badge ? loadIcon(context().getPackageManager()) : loadUnbadgedIconCompat(context().getPackageManager());
				}

				@Override protected void onPostExecute(final Drawable drawable) {
					if (drawable == null) return;        // Might be null if app is currently being removed.
					final Drawable icon = (filter != null ? filter.process(drawable) : drawable);
					mCachedIcon = icon;
					consumer.accept(icon);
				}
			}.executeOnExecutor(TASK_THREAD_POOL);
		} catch (final RejectedExecutionException e) {
			Analytics.$().report(e);        // For statistics purpose
		} else consumer.accept(mCachedIcon);
	}

	/** Called by {@link AppListProvider#onTrimMemory(int)} to trim memory when UI is hidden */
	@CallSuper void trimMemoryOnUiHidden() {
		mCachedIcon = null;
	}

	/** Called by {@link AppListProvider#onTrimMemory(int)} to trim memory in memory-critical situation */
	@CallSuper void trimMemoryOnCritical() {
		mCachedIcon = null;
		mLastInfo = null;
		// mLabel is not worth trimming and kept for performance
	}

	private static <T> Supplier<T> lazyLessMutable(final Supplier<T> supplier) { return Suppliers.memoizeWithExpiration(supplier, 1, SECONDS); }

	private Drawable loadUnbadgedIconCompat(final PackageManager pm) {
		try {
			return loadUnbadgedIcon(pm);
		} catch (final SecurityException e) {		// Appears on some Samsung devices (e.g. Galaxy S7, Note 8) with Android 8.0
			Analytics.$().logAndReport(TAG, "Error loading unbadged icon for " + this, e);
		}
		Drawable dr = null;
		if (packageName != null) dr = pm.getDrawable(packageName, icon, this);
		if (dr == null) dr = pm.getDefaultActivityIcon();
		return dr;
	}

	@NonNull public Context context() { return mProvider.context(); }

	@Override public @NonNull String toString() { return buildToString(AppInfo.class).append('}').toString(); }

	protected StringBuilder buildToString(final Class<?> clazz) {
		final StringBuilder builder = new StringBuilder(clazz.getSimpleName()).append('{').append(packageName);
		if (! isInstalled()) builder.append(", not installed");
		if (isSystem()) builder.append(", system");
		if (! enabled) builder.append(", disabled");
		return builder;
	}

	protected final AppListProvider<? extends AppInfo> mProvider;
	private final String mLabel;
	private Drawable mCachedIcon;
	/** The information about the same package before its state is changed to this instance, may not always be kept over time */
	private AppInfo mLastInfo;
	// Global Thread-pool for app label & icon loading
	static final ThreadPoolExecutor TASK_THREAD_POOL = new ThreadPoolExecutor(0, 8, 1, SECONDS,
			new LinkedBlockingQueue<>(1024), r -> new Thread(r, "AppInfo.AsyncTask"), new CallerRunsPolicy()/* In worst case */);
	static { TASK_THREAD_POOL.allowCoreThreadTimeOut(true); }

	private static final String TAG = "AppInfo";
}
