package com.oasisfeng.island.util;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O_MR1;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.S;
import static com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.print.PrintManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.oasisfeng.android.annotation.UserIdInt;
import com.oasisfeng.hack.Hack;
import com.oasisfeng.hack.Hack.Unchecked;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.shared.BuildConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * All reflection-based hacks should be defined here
 *
 * Created by Oasis on 2016/8/10.
 */
public class Hacks {

	static {
		Hack.setAssertionFailureHandler(e -> {
			Log.e("Compatibility", e.getDebugInfo());
			if (BuildConfig.DEBUG) throw new IllegalStateException("Incompatibility", e);
			if (Users.isParentProfile()) Analytics.$().report(e);
		});
	}

	private static final int MATCH_ANY_USER = 0x00400000;		// Requires INTERACT_ACROSS_USERS since Android P.
	/**
	 * When used in @ApplicationInfoFlags or @PackageInfoFlags:
	 *   For system user, GET_UNINSTALLED_PACKAGES implicitly set MATCH_ANY_USER.
	 *   For managed profile, MATCH_ANY_USER requires permission INTERACT_ACROSS_USERS since Android P.
	 * When used in @ComponentInfoFlags or @ResolveInfoFlags: MATCH_ANY_USER is always allowed.
	 *
	 * See PackageManagerService.updateFlagsForPackage()
	 */
	public static final int MATCH_ANY_USER_AND_UNINSTALLED = PackageManager.MATCH_UNINSTALLED_PACKAGES | (Users.isSystemUser() ? 0 : MATCH_ANY_USER);
	public static final int RESOLVE_ANY_USER_AND_UNINSTALLED = PackageManager.MATCH_UNINSTALLED_PACKAGES | MATCH_ANY_USER;

	public static final Hack.HackedField<ApplicationInfo, Integer>
			ApplicationInfo_privateFlags = Hack.into(ApplicationInfo.class).field("privateFlags").fallbackTo(null);
	public static final Hack.HackedField<ApplicationInfo, Integer>
			ApplicationInfo_versionCode = Hack.into(ApplicationInfo.class).field("versionCode").fallbackTo(0);
	public static final Hack.HackedTargetField<String>
			PrintManager_PRINT_SPOOLER_PACKAGE_NAME = Hack.onlyIf(SDK_INT <= O_MR1).into(PrintManager.class)
			.staticField("PRINT_SPOOLER_PACKAGE_NAME").fallbackTo("com.android.printspooler");
	public static final Hack.HackedField<PowerManager, Object>
			PowerManager_mService = Hack.into(PowerManager.class).field("mService").fallbackTo(null);
	public static final Hack.HackedField<AppWidgetProviderInfo, ActivityInfo>
			AppWidgetProviderInfo_providerInfo = Hack.onlyIf(SDK_INT < S).into(AppWidgetProviderInfo.class)
			.field("providerInfo").fallbackTo(null);

	public static final Hack.HackedMethod2<Integer, Void, Unchecked, Unchecked, Unchecked, String, Integer>
			SystemProperties_getInt = Hack.into("android.os.SystemProperties").staticMethod("getInt")
			.returning(int.class).fallbackReturning(null).withParams(String.class, int.class);
	public static final Hack.HackedMethod1<String, Void, Unchecked, Unchecked, Unchecked, String>
			SystemProperties_get = Hack.into("android.os.SystemProperties").staticMethod("get")
			.returning(String.class).fallbackReturning(null).withParam(String.class);
	static final Hack.HackedMethod0<ComponentName, DevicePolicyManager, IllegalArgumentException, Unchecked, Unchecked>
			DevicePolicyManager_getProfileOwner = Hack.into(DevicePolicyManager.class).method("getProfileOwner")
			.returning(ComponentName.class).fallbackReturning(null).throwing(IllegalArgumentException.class).withoutParams();
	static final Hack.HackedMethod1<ComponentName, DevicePolicyManager, IllegalArgumentException, Unchecked, Unchecked, Integer>
			DevicePolicyManager_getProfileOwnerAsUser = Hack.onlyIf(SDK_INT < P).into(DevicePolicyManager.class).method("getProfileOwnerAsUser")
			.returning(ComponentName.class).fallbackReturning(null).throwing(IllegalArgumentException.class).withParam(int.class);
	static final Hack.HackedMethod0<String, DevicePolicyManager, Unchecked, Unchecked, Unchecked>
			DevicePolicyManager_getDeviceOwner = Hack.into(DevicePolicyManager.class).method("getDeviceOwner")
			.returning(String.class).fallbackReturning(null).withoutParams();
	public static final Hack.HackedMethod2<int[], UserManager, Unchecked, Unchecked, Unchecked, Integer, Boolean>
			UserManager_getProfileIds = Hack.onlyIf(SDK_INT <= O_MR1).into(UserManager.class).method("getProfileIds")
			.returning(int[].class).fallbackReturning(null).withParams(int.class, boolean.class);
	public static final Hack.HackedMethod3<Context, Context, NameNotFoundException, Unchecked, Unchecked, String, Integer, UserHandle>
			Context_createPackageContextAsUser = Hack.into(Context.class).method("createPackageContextAsUser").returning(Context.class)
			.fallbackReturning(null).throwing(NameNotFoundException.class).withParams(String.class, int.class, UserHandle.class);
	static final Hack.HackedMethod2<Context, Context, NameNotFoundException, Unchecked, Unchecked, ApplicationInfo, Integer>
			Context_createApplicationContext = Hack.into(Context.class).method("createApplicationContext").returning(Context.class)
			.fallbackReturning(null).throwing(NameNotFoundException.class).withParams(ApplicationInfo.class, int.class);
	static final Hack.HackedMethod0<?, Void, Unchecked, Unchecked, Unchecked>
			ActivityManagerNative_getDefault = Hack.into("android.app.ActivityManagerNative")
			.staticMethod("getDefault").fallbackReturning(null).withoutParams();
	static final Hack.HackedMethod0<IBinder, Activity, Unchecked, Unchecked, Unchecked>
			Activity_getActivityToken = Hack.into(Activity.class).method("getActivityToken")
			.returning(IBinder.class).fallbackReturning(null).withoutParams();
	static final Hack.HackedMethod1<Integer, Object, RemoteException, Unchecked, Unchecked, IBinder>
			IActivityManager_getLaunchedFromUid = Hack.into("android.app.IActivityManager")
			.method("getLaunchedFromUid").returning(int.class).throwing(RemoteException.class).withParam(IBinder.class);
	static final Hack.HackedMethod1<String, Object, RemoteException, Unchecked, Unchecked, IBinder>
			IActivityManager_getLaunchedFromPackage = Hack.into("android.app.IActivityManager")
			.method("getLaunchedFromPackage").returning(String.class).throwing(RemoteException.class).withParam(IBinder.class);

	@ParametersAreNonnullByDefault public interface AppOpsManager extends Hack.Mirror<android.app.AppOpsManager> {

		@Retention(RetentionPolicy.SOURCE) @IntDef(flag = true, value = {
				android.app.AppOpsManager.MODE_ALLOWED,
				android.app.AppOpsManager.MODE_IGNORED,
				android.app.AppOpsManager.MODE_ERRORED,
				android.app.AppOpsManager.MODE_DEFAULT,
				android.app.AppOpsManager.MODE_FOREGROUND
		}) @interface Mode {}

		interface PackageOps extends Hack.Mirror<Object> {
			String getPackageName();
			int getUid();
			List<OpEntry> getOps();
		}

		interface OpEntry extends Hack.Mirror<Object> {
			int OP_FALL_BACK = -9;
			@Hack.Fallback(OP_FALL_BACK) int getOp();
			int getMode();
		}

		@Hack.Fallback(-1) int checkOpNoThrow(int op, int uid, String pkg);
		@RequiresPermission(GET_APP_OPS_STATS) @Nullable List<PackageOps> getOpsForPackage(int uid, String pkg, @Nullable int[] ops);
		@RequiresPermission(GET_APP_OPS_STATS) @Nullable List<PackageOps> getPackagesForOps(@Nullable int[] ops);
		void setMode(int code, int uid, String packageName, @Mode int mode);
		void setUidMode(String appOp, int uid, @Mode int mode);
		void setRestriction(int code,/* @AttributeUsage */int usage, @Mode int mode, @Nullable String[] exceptionPackages);

		/** Retrieve the default mode for the operation. */
		@Hack.Fallback(-1) @Mode int opToDefaultMode(final int op);
	}

	public interface UserManagerHack extends Hack.Mirror<UserManager> {

		@Hack.SourceClass("android.content.pm.UserInfo") interface UserInfo extends Hack.Mirror<Object> {
			int getId();
			UserHandle getUserHandle();
		}

		/** Requires permission MANAGE_USERS only if userHandle is not current user */
		List<UserInfo> getProfiles(int userHandle);
		@RequiresPermission("android.permission.MANAGE_USERS") List<UserInfo> getUsers();
		boolean removeUser(@UserIdInt int userHandle);
	}

	public interface PackageManagerHack extends Hack.Mirror<PackageManager> {
		ComponentName getHomeActivities(List<ResolveInfo> outActivities);
	}

	static { if (BuildConfig.DEBUG || Log.isLoggable("Island", Log.DEBUG)) Hack.verifyAllMirrorsIn(Hacks.class); }

//	public static final @Nullable Hack.HackedMethod0<File, Void, Unchecked, Unchecked, Unchecked>
//			Environment_getDataSystemDirectory = Hack.into(Environment.class).staticMethod("getDataSystemDirectory")
//			.returning(File.class).withoutParams();
}
