package com.oasisfeng.island.shortcut;

import android.content.ComponentName;
import android.widget.Toast;

import com.oasisfeng.island.engine.IslandManagerService;
import com.oasisfeng.island.engine.R;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Implementation of {@link AbstractAppLaunchShortcut}
 *
 * Created by Oasis on 2017/2/19.
 */
@ParametersAreNonnullByDefault
public class AppLaunchShortcut extends AbstractAppLaunchShortcut {

	@Override protected boolean prepareToLaunchApp(final ComponentName component) {
		try {	// Skip all checks and unfreeze it straightforward, to eliminate as much cost as possible in the most common case. (app is frozen)
			return new IslandManagerService(this).unfreezeApp(component.getPackageName());
		} catch (final SecurityException e) { return false; }	// If not profile owner or profile owner.
	}

	@Override protected void onLaunchFailed() {
		Toast.makeText(this, R.string.toast_shortcut_invalid, Toast.LENGTH_LONG).show();
	}
}
