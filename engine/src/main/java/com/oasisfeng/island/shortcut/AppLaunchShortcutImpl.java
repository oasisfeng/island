package com.oasisfeng.island.shortcut;

import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;
import android.widget.Toast;

import com.oasisfeng.island.R;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.IslandManagerService;
import com.oasisfeng.island.util.Users;

/**
 * Implementation of {@link AppLaunchShortcut}
 *
 * Created by Oasis on 2017/2/19.
 */
public class AppLaunchShortcutImpl extends AppLaunchShortcut {

	protected boolean prepareToLaunchApp(final ComponentName component) {
		final UserHandle user = Process.myUserHandle();
		if (Users.isOwner(user) && ! new IslandManager(this).isDeviceOwner()) return false;

		// Ensure de-frozen
		new IslandManagerService(this).unfreezeApp(component.getPackageName());
		return true;
	}

	@Override protected void onLaunchFailed() {
		Toast.makeText(this, R.string.toast_shortcut_invalid, Toast.LENGTH_LONG).show();
	}
}
