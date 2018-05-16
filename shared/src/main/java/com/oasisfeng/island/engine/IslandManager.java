package com.oasisfeng.island.engine;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.CheckResult;
import android.support.annotation.RequiresApi;

import com.google.common.base.Preconditions;
import com.oasisfeng.android.service.Services;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Users;

import java.lang.reflect.Proxy;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static android.os.Build.VERSION_CODES.N;

/**
 * Controller of Island
 *
 * Created by Oasis on 2017/2/20.
 */
@ParametersAreNonnullByDefault
public class IslandManager {

	public static final int CLONE_RESULT_ALREADY_CLONED = 0;
	public static final int CLONE_RESULT_OK_INSTALL = 1;
	public static final int CLONE_RESULT_OK_SYS_APP = 2;
	public static final int CLONE_RESULT_OK_GOOGLE_PLAY = 10;
	public static final int CLONE_RESULT_UNKNOWN_SYS_MARKET = 11;
	public static final int CLONE_RESULT_NOT_FOUND = -1;
	public static final int CLONE_RESULT_NO_SYS_MARKET = -2;

	static class NotConnectedException extends RemoteException { @Override public String getMessage() { return "Engine not connected"; }}

	public static final IIslandManager NULL = (IIslandManager) Proxy.newProxyInstance(IIslandManager.class.getClassLoader(), new Class[] {IIslandManager.class}, (proxy, method, args) -> {
		if ("toString".equals(method.getName())) return "NULL";		// To avoid UndeclaredThrowableException.
		throw new NotConnectedException();
	});

	public static boolean launchApp(final Context context, final String pkg, final UserHandle profile) {
		final LauncherApps launcher_apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
		if (launcher_apps == null) return false;
		final List<LauncherActivityInfo> activities = launcher_apps.getActivityList(pkg, profile);
		if (activities == null || activities.isEmpty()) return false;
		launcher_apps.startMainActivity(activities.get(0).getComponentName(), profile, null, null);
		return true;
	}

	public void deactivateDeviceOwner() {
		Analytics.$().event("action_deactivate").send();
		mDevicePolicies.getManager().clearDeviceOwnerApp(mContext.getPackageName());
		try {
			mDevicePolicies.removeActiveAdmin();			// Since Android 7.1, clearDeviceOwnerApp() itself does remove active device-admin,
		} catch (final SecurityException ignored) {}		//   thus SecurityException will be thrown here.
	}

	/** @return profile ID, or 0 if none */
	@RequiresApi(N) public static int getManagedProfileIdIncludingDisabled(final Context context) {
		final int[] profiles = Hacks.UserManager_getProfileIds.invoke(Process.myUserHandle().hashCode(), false)
				.on(Preconditions.checkNotNull(context.getSystemService(UserManager.class)));
		final int current_user = Process.myUserHandle().hashCode();
		if (profiles != null) for (final int profile : profiles)
			if (profile != current_user) return profile;   			// Only one managed profile is supported by Android at present.
		return 0;
	}

	public static @CheckResult boolean useServiceInProfile(final Context context, final Services.ServiceReadyThrows<IIslandManager, RemoteException> procedure) {
		return Services.use(context instanceof ShuttleContext || Users.isProfile() ? context : new ShuttleContext(context),
				IIslandManager.class, IIslandManager.Stub::asInterface, procedure);
	}

	public static @CheckResult boolean useServiceInOwner(final Context context, final Services.ServiceReadyThrows<IIslandManager, RemoteException> procedure) {
		return Services.use(context, IIslandManager.class, IIslandManager.Stub::asInterface, procedure);
	}

	public IslandManager(final Context context) {
		mContext = context;
		mDevicePolicies = new DevicePolicies(context);
	}

	private final Context mContext;
	private final DevicePolicies mDevicePolicies;
}
