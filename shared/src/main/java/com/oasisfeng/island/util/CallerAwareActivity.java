package com.oasisfeng.island.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import androidx.annotation.Nullable;

/**
 * Activity with enhanced {@link #getCallingPackage()}, which detects caller even if not started by {@link #startActivityForResult(Intent, int)}.
 *
 * Created by Oasis on 2019-2-27.
 */
public abstract class CallerAwareActivity extends Activity {

	@Override public @Nullable String getCallingPackage() {
		final String caller = super.getCallingPackage();
		if (caller != null) return caller;
		return getCallingPackage(this);
	}

	public static @Nullable String getCallingPackage(final Activity activity) {
		Intent original_intent = null;
		final Intent intent = activity.getIntent();
		if (intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)) {
			original_intent = new Intent(activity.getIntent());
			intent.removeExtra(Intent.EXTRA_REFERRER);
			intent.removeExtra(Intent.EXTRA_REFERRER_NAME);
		}
		try {
			final Uri referrer = activity.getReferrer();		// getReferrer() returns real calling package if no referrer extras
			if (referrer != null) return referrer.getAuthority();        // Referrer URI: android-app://<package name>
		} finally {
			if (original_intent != null) activity.setIntent(original_intent);
		}
		if (Hacks.IActivityManager_getLaunchedFromPackage != null) try {
			final Object am = Hacks.ActivityManagerNative_getDefault.invoke().statically();
			if (am != null) {
				final IBinder token = Hacks.Activity_getActivityToken.invoke().on(activity);
				if (token != null) return Hacks.IActivityManager_getLaunchedFromPackage.invoke(token).on(am);
			}
		} catch (final Exception e) {
			Log.e(TAG, "Error detecting caller", e);
		}
		return null;
	}

	public static int getCallingUid(final Activity activity) {
		if (Hacks.IActivityManager_getLaunchedFromUid != null) try {
			final Object am = Hacks.ActivityManagerNative_getDefault.invoke().statically();
			if (am != null) {
				final IBinder token = Hacks.Activity_getActivityToken.invoke().on(activity);
				if (token != null) return Hacks.IActivityManager_getLaunchedFromUid.invoke(token).on(am);
			}
		} catch (final Exception e) {
			Log.e(TAG, "Error detecting caller", e);
		}
		return Process.INVALID_UID;
	}

	private static final String TAG = "Island.CAA";
}
