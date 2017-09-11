package com.oasisfeng.island.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.print.PrintManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.oasisfeng.hack.Hack;
import com.oasisfeng.hack.Hack.Unchecked;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.shared.BuildConfig;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * All reflection-based hacks should be defined here
 *
 * Created by Oasis on 2016/8/10.
 */
public class Hacks {

	public static final int PackageManager_MATCH_ANY_USER = 0x00400000;

	public static final Hack.HackedField<ApplicationInfo, Integer> ApplicationInfo_privateFlags;
	public static final Hack.HackedField<ApplicationInfo, Integer> ApplicationInfo_versionCode;
	public static final Hack.HackedTargetField<String> PrintManager_PRINT_SPOOLER_PACKAGE_NAME;

	public static final Hack.HackedMethod2<Boolean, Void, Unchecked, Unchecked, Unchecked, String, Boolean> SystemProperties_getBoolean;
	public static final Hack.HackedMethod2<Integer, Void, Unchecked, Unchecked, Unchecked, String, Integer> SystemProperties_getInt;
	public static final Hack.HackedMethod1<ComponentName, DevicePolicyManager, IllegalArgumentException, Unchecked, Unchecked, Integer> DevicePolicyManager_getProfileOwnerAsUser;
	public static final Hack.HackedMethod0<String, DevicePolicyManager, Unchecked, Unchecked, Unchecked> DevicePolicyManager_getDeviceOwner;
	@RequiresApi(N) public static Hack.HackedMethod3<ApplicationInfo, LauncherApps, Exception, Unchecked, Unchecked, String, Integer, UserHandle> LauncherApps_getApplicationInfo;
	public static final Hack.HackedMethod4<Boolean, Context, Unchecked, Unchecked, Unchecked, Intent, ServiceConnection, Integer, UserHandle> Context_bindServiceAsUser;
	public static final Hack.HackedMethod3<ResolveInfo, PackageManager, Unchecked, Unchecked, Unchecked, Intent, Integer, Integer> PackageManager_resolveActivityAsUser;
	@RequiresApi(N) public static Hack.HackedMethod2<int[], UserManager, Unchecked, Unchecked, Unchecked, Integer, Boolean> UserManager_getProfileIds;
	public static final Hack.HackedMethod2<Context, Context, NameNotFoundException, Unchecked, Unchecked, ApplicationInfo, Integer> Context_createApplicationContext;
	public static final Hack.HackedMethod3<Context, Context, NameNotFoundException, Unchecked, Unchecked, String, Integer, UserHandle> Context_createPackageContextAsUser;
	public static final Hack.HackedMethodN<IBinder, Void, Unchecked, Unchecked, Unchecked> ServiceManager_getService;
	public static final Hack.HackedMethod1<?, Void, Unchecked, Unchecked, Unchecked, IBinder> IWebViewUpdateService$Stub_asInterface;
	@RequiresApi(N) public static Hack.HackedMethod0<String, Object, RemoteException, Unchecked, Unchecked> IWebViewUpdateService_getCurrentWebViewPackageName;

	static {
		Hack.setAssertionFailureHandler(e -> {
			Log.e("Compatibility", e.getDebugInfo());
			if (BuildConfig.DEBUG) throw new IllegalStateException("Incompatibility", e);
			if (Users.isOwner()) Analytics.$().report(e);
		});

		ApplicationInfo_privateFlags = Hack.onlyIf(SDK_INT >= M).into(ApplicationInfo.class).field("privateFlags").fallbackTo(null);
		ApplicationInfo_versionCode = Hack.into(ApplicationInfo.class).field("versionCode").fallbackTo(0);
		PrintManager_PRINT_SPOOLER_PACKAGE_NAME = Hack.onlyIf(SDK_INT >= N).into(PrintManager.class)
				.staticField("PRINT_SPOOLER_PACKAGE_NAME").fallbackTo("com.android.printspooler");

		SystemProperties_getBoolean = Hack.into("android.os.SystemProperties").staticMethod("getBoolean")
				.returning(boolean.class).fallbackReturning(false).withParams(String.class, boolean.class);
		SystemProperties_getInt = Hack.into("android.os.SystemProperties").staticMethod("getInt")
				.returning(int.class).fallbackReturning(null).withParams(String.class, int.class);
		DevicePolicyManager_getProfileOwnerAsUser = Hack.into(DevicePolicyManager.class).method("getProfileOwnerAsUser")
				.returning(ComponentName.class).fallbackReturning(null).throwing(IllegalArgumentException.class).withParam(int.class);
		DevicePolicyManager_getDeviceOwner = Hack.into(DevicePolicyManager.class).method("getDeviceOwner")
				.returning(String.class).fallbackReturning(null).withoutParams();
		if (SDK_INT >= N) LauncherApps_getApplicationInfo = Hack.into(LauncherApps.class).method("getApplicationInfo").throwing()
				.returning(ApplicationInfo.class).fallbackReturning(null).withParams(String.class, int.class, UserHandle.class);
		Context_bindServiceAsUser = Hack.into(Context.class).method("bindServiceAsUser").returning(boolean.class).fallbackReturning(false)
				.withParams(Intent.class, ServiceConnection.class, int.class, UserHandle.class);
		PackageManager_resolveActivityAsUser = Hack.into(PackageManager.class).method("resolveActivityAsUser")
				.returning(ResolveInfo.class).fallbackReturning(null).withParams(Intent.class, int.class, int.class);
		if (SDK_INT >= N) UserManager_getProfileIds = Hack.into(UserManager.class).method("getProfileIds")
				.returning(int[].class).fallbackReturning(null).withParams(int.class, boolean.class);
		Context_createApplicationContext = Hack.into(Context.class).method("createApplicationContext").returning(Context.class)
				.throwing(NameNotFoundException.class).withParams(ApplicationInfo.class, int.class);
		Context_createPackageContextAsUser = Hack.into(Context.class).method("createPackageContextAsUser").returning(Context.class)
				.fallbackReturning(null).throwing(NameNotFoundException.class).withParams(String.class, int.class, UserHandle.class);
		ServiceManager_getService = Hack.into("android.os.ServiceManager").staticMethod("getService")
				.returning(IBinder.class).fallbackReturning(null).withParams(String.class);
		final String IWebViewUpdateService = "android.webkit.IWebViewUpdateService";
		IWebViewUpdateService$Stub_asInterface = Hack.into(IWebViewUpdateService + "$Stub").staticMethod("asInterface")
				.returning(Hack.ANY_TYPE).withParam(IBinder.class);
		if (SDK_INT >= N) IWebViewUpdateService_getCurrentWebViewPackageName = Hack.into(IWebViewUpdateService).method("getCurrentWebViewPackageName")
				.returning(String.class).fallbackReturning(null).throwing(RemoteException.class).withoutParams();
	}
}
