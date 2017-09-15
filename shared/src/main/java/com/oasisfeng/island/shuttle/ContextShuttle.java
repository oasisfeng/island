package com.oasisfeng.island.shuttle;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;

import com.oasisfeng.island.util.Hacks;

import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

/**
 * Utility class for cross-user context related stuffs.
 *
 * Created by Oasis on 2017/9/1.
 */
public class ContextShuttle {

	@RequiresPermission(INTERACT_ACROSS_USERS)
	public static @Nullable PackageManager getPackageManagerAsUser(final Context context, final UserHandle user) {
		try {
			final Context user_context = Hacks.Context_createPackageContextAsUser.invoke("system", 0, user).on(context);
			return user_context != null ? user_context.getPackageManager() : null;
		} catch (final PackageManager.NameNotFoundException ignored) { return null; }		// Should never happen
	}
}
