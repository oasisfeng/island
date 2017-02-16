package com.oasisfeng.island.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.oasisfeng.android.service.Services;
import com.oasisfeng.android.util.SafeSharedPreferences;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.util.OwnerUser;

import java8.util.function.Consumer;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

/** Track explicitly "cloned" (unfrozen) system apps (previous frozen in post-provisioning). Other frozen system apps should be treated as "disabled" in UI. */
@OwnerUser public class ClonedHiddenSystemApps {

	private static final String SHARED_PREFS_PREFIX_ENABLED_SYSTEM_APPS = "cloned_system_apps_u"/* + user serial number */;
	private static final String PREF_KEY_VERSION = "_version";
	private static final int CURRENT_VERSION = 1;

	/** @return {@link PackageManager#COMPONENT_ENABLED_STATE_DEFAULT} or {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED} */
	public boolean isCloned(final String pkg) {
		return mStore.getInt(pkg, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
	}

	public void setCloned(final String pkg) {
		mStore.edit().putInt(pkg, PackageManager.COMPONENT_ENABLED_STATE_ENABLED).apply();
	}

	public ClonedHiddenSystemApps(final Context context, final UserHandle user, final Consumer<String> update_observer) {
		mUser = user;
		mStore = getStore(context, user);
		mStore.registerOnSharedPreferenceChangeListener(mChangeListener);
		mUpdateObserver = update_observer;
	}

	public static void reset(final Context context, final UserHandle user) {
		getStore(context, user).edit().clear().apply();
	}

	/** Must be called only once in one process, preferably by lazy-initialization in content provider. */
	@OwnerUser public void initializeIfNeeded(final Context context) {
		final int version = mStore.getInt(PREF_KEY_VERSION, 0);
		if (version < CURRENT_VERSION) initialize(context);
	}

	@OwnerUser private void initialize(final Context context) {
		final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
		final long begin_time;
		if (SDK_INT >= M) begin_time = um.getUserCreationTime(mUser);
		else try { begin_time = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime; }
		catch (final PackageManager.NameNotFoundException e) { throw new IllegalStateException("Cannot retrieve package info"); }

		Services.use(new ShuttleContext(context), IIslandManager.class, IIslandManager.Stub::asInterface, island -> {
			final String[] used_pkgs = island.queryUsedPackagesDuring(begin_time, System.currentTimeMillis());
			final SharedPreferences.Editor editor = mStore.edit().clear();
			if (used_pkgs.length > 0) {
				final IslandAppListProvider apps = IslandAppListProvider.getInstance(context);
				for (final String pkg : used_pkgs) {
					final IslandAppInfo app = apps.get(pkg, mUser);
					if (app == null || ! app.isSystem()) continue;
					editor.putInt(pkg, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
				}
			} else Log.w(TAG, "No used system apps");		// Failed to query, may not have required permission (PACKAGE_USAGE_STATS), fall-back to treat all hidden as disabled.
			editor.putInt(PREF_KEY_VERSION, 1).apply();
		});
	}

	private static SharedPreferences getStore(final Context context, final UserHandle user) {
		final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
		final long usn = um.getSerialNumberForUser(user);
		return SafeSharedPreferences.wrap(context.getSharedPreferences(SHARED_PREFS_PREFIX_ENABLED_SYSTEM_APPS + usn, Context.MODE_PRIVATE));
	}

	@SuppressWarnings("FieldCanBeLocal") private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener = (sp, key) -> {
		Log.d(TAG, "Updated: " + key);
		if (key.startsWith("_")) return;
		ClonedHiddenSystemApps.this.mUpdateObserver.accept(key);
	};

	private final SharedPreferences mStore;
	private final UserHandle mUser;
	private final Consumer<String> mUpdateObserver;

	private static final String TAG = "Island.HiddenSysApp";
}
