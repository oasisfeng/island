package com.oasisfeng.island.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.hack.Hack;
import com.oasisfeng.hack.Hack.Unchecked;
import com.oasisfeng.island.analytics.Analytics;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * All reflection-based hacks should be defined here
 *
 * Created by Oasis on 2016/8/10.
 */
public class Hacks {

	public interface Permission {
		String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";
	}

	public static final Hack.HackedField<ApplicationInfo, Integer> ApplicationInfo_privateFlags;
	public static final Hack.HackedField<ApplicationInfo, Integer> ApplicationInfo_versionCode;

	public static final Hack.HackedMethod2<Boolean, Void, Unchecked, Unchecked, Unchecked, String, Boolean> SystemProperties_getBoolean;
	public static final Hack.HackedMethod2<Integer, Void, Unchecked, Unchecked, Unchecked, String, Integer> SystemProperties_getInt;
	public static final Hack.HackedMethod1<ComponentName, DevicePolicyManager, IllegalArgumentException, Unchecked, Unchecked, Integer> DevicePolicyManager_getProfileOwnerAsUser;
	public static final Hack.HackedMethod3<ApplicationInfo, LauncherApps, Unchecked, Unchecked, Unchecked, String, Integer, UserHandle> LauncherApps_getApplicationInfo;
	public static final Hack.HackedMethod4<Boolean, Context, Unchecked, Unchecked, Unchecked, Intent, ServiceConnection, Integer, UserHandle> Context_bindServiceAsUser;

	static {
		Hack.setAssertionFailureHandler(e -> {
			Log.e("Compatibility", e.getDebugInfo());
			if (Users.isOwner()) Analytics.$().event("compat_hacks").with("message", e.getMessage()).with("info", e.getDebugInfo()).send();
		});

		ApplicationInfo_privateFlags = Hack.onlyIf(SDK_INT >= M).into(ApplicationInfo.class).field("privateFlags").fallbackTo(null);
		ApplicationInfo_versionCode = Hack.into(ApplicationInfo.class).field("versionCode").fallbackTo(0);

		SystemProperties_getBoolean = Hack.into("android.os.SystemProperties").staticMethod("getBoolean")
				.returning(boolean.class).fallbackReturning(false).withParams(String.class, boolean.class);
		SystemProperties_getInt = Hack.into("android.os.SystemProperties").staticMethod("getInt")
				.returning(int.class).fallbackReturning(null).withParams(String.class, int.class);
		DevicePolicyManager_getProfileOwnerAsUser = Hack.into(DevicePolicyManager.class).method("getProfileOwnerAsUser")
				.returning(ComponentName.class).fallbackReturning(null).throwing(IllegalArgumentException.class).withParam(int.class);
		LauncherApps_getApplicationInfo = Hack.onlyIf(SDK_INT >= N).into(LauncherApps.class).method("getApplicationInfo")
				.returning(ApplicationInfo.class).fallbackReturning(null).withParams(String.class, int.class, UserHandle.class);
		Context_bindServiceAsUser = Hack.into(Context.class).method("bindServiceAsUser").returning(boolean.class).fallbackReturning(false)
				.withParams(Intent.class, ServiceConnection.class, int.class, UserHandle.class);
	}
}
