package com.oasisfeng.island.api;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.RemoteException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.oasisfeng.island.data.AppList;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.engine.SystemAppsManager;

import java.util.List;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

/**
 * AIDL service behind {@link IAppsController}
 *
 * Created by Oasis on 2016/6/2.
 */

public class AppControllerService extends Service {

	@Override public IBinder onBind(final Intent intent) {
		// TODO: Verify caller signature.
		return new AppController();
	}

	private class AppController extends IAppsController.Stub {

		@Override public List<String> getInstalledApps(final int flags) throws RemoteException {
			final ImmutableList<ApplicationInfo> apps = AppList.populate(AppControllerService.this)
					.filter(app -> (app.flags & FLAG_SYSTEM) == 0 || AppList.ALWAYS_VISIBLE_SYS_PKGS.contains(app.packageName)
					|| (! SystemAppsManager.isCritical(app.packageName) && IslandManager.isLaunchable(AppControllerService.this, app.packageName)))
					.toSortedList(AppList.CLONED_FIRST);
			return Lists.transform(apps, info -> info.packageName);
		}

		@Override public void freeze(final String pkg) throws RemoteException {
			new IslandManager(AppControllerService.this).freezeApp(pkg, "api");
		}
	}
}
