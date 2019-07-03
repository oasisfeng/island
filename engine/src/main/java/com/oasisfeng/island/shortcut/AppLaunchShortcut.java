package com.oasisfeng.island.shortcut;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.util.Cryptography;

import java.security.GeneralSecurityException;

import javax.annotation.ParametersAreNonnullByDefault;

import static android.content.Intent.CATEGORY_LAUNCHER;

/**
 * Implementation of {@link AbstractAppLaunchShortcut}
 *
 * Created by Oasis on 2017/2/19.
 */
@ParametersAreNonnullByDefault
public class AppLaunchShortcut extends AbstractAppLaunchShortcut {

	public static final String EXTRA_SIGNATURE = "signature";		// String

	public static Intent createShortcutOnLauncher(final Intent target_intent) {
		final Uri target_intent_uri = Uri.parse(target_intent.toUri(Intent.URI_INTENT_SCHEME)).buildUpon().scheme("target").build();
		return new Intent(ACTION_LAUNCH_CLONE).addCategory(CATEGORY_LAUNCHER).setData(target_intent_uri);
	}

	protected boolean validateIncomingIntent(final Intent target, final Intent outer) {
		final String signature = outer.getStringExtra(EXTRA_SIGNATURE);
		final String target_uri = target.toUri(0);
		try {
			return Cryptography.verify(target_uri, signature);
		} catch (final GeneralSecurityException e) {
			if (signature != null)		// Lacking of signature is usually caused by signing exception, so we just skip the verification.
				Analytics.$().logAndReport(TAG, "Error verifying intent: " + target_uri, e);
			return true;	// Always bypass the validation in case of cryptography exception.
		}
	}

	@Override protected boolean prepareToLaunchApp(final String pkg) {
		try {	// Skip all checks and unfreeze it straightforward, to eliminate as much cost as possible in the most common case. (app is frozen)
			return IslandManager.ensureAppHiddenState(this, pkg, false);
		} catch (final SecurityException e) { return false; }	// If not profile owner or profile owner.
	}

	@Override protected void onLaunchFailed() {
		Toast.makeText(this, R.string.toast_shortcut_invalid, Toast.LENGTH_LONG).show();
	}

	private static final String TAG = "AppLaunchShortcut";
}
