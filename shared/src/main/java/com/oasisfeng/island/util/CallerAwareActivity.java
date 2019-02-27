package com.oasisfeng.island.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;

/**
 * Activity with enhanced {@link #getCallingPackage()}, which detects caller even if not started by {@link #startActivityForResult(Intent, int)}.
 *
 * Created by Oasis on 2019-2-27.
 */
public abstract class CallerAwareActivity extends Activity {

	@Nullable @Override public String getCallingPackage() {
		final String caller = super.getCallingPackage();
		if (caller != null) return caller;

		if (SDK_INT >= LOLLIPOP_MR1) {
			Intent original_intent = null;
			final Intent intent = getIntent();
			if (intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)) {
				original_intent = new Intent(getIntent());
				intent.removeExtra(Intent.EXTRA_REFERRER);
				intent.removeExtra(Intent.EXTRA_REFERRER_NAME);
			}
			try {
				final Uri referrer = getReferrer();		// getReferrer() returns real calling package if no referrer extras
				if (referrer != null) return referrer.getAuthority();        // Referrer URI: android-app://<package name>
			} finally {
				if (original_intent != null) setIntent(original_intent);
			}
		}
		try {
			@SuppressLint("PrivateApi") final Object am = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
			@SuppressWarnings("JavaReflectionMemberAccess") final Object token = Activity.class.getMethod("getActivityToken").invoke(this);
			return (String) am.getClass().getMethod("getLaunchedFromPackage", IBinder.class).invoke(am, (IBinder) token);
		} catch (final Exception e) {
			Log.e(TAG, "Error detecting caller", e);
		}
		return null;
	}

	private static final String TAG = "Island.CAA";
}
