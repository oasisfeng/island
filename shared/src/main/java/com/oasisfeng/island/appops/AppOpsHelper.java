package com.oasisfeng.island.appops;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.ProfileUser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS;

/**
 * Created by Oasis on 2019-3-1.
 */
@RequiresApi(28) public class AppOpsHelper {

	private static final String PREFS_NAME = "app_ops";

	/** @return whether anything is saved. False will be returned for failure */
	@ProfileUser @RequiresPermission(GET_APP_OPS_STATS)
	public static boolean saveAppOps(final Context context, final String pkg) throws PackageManager.NameNotFoundException {
		final List<Hacks.AppOpsManager.PackageOps> list = new AppOpsCompat(context)
				.getOpsForPackage(context.getPackageManager().getPackageUid(pkg, MATCH_DISABLED_COMPONENTS), pkg, null);
		final SharedPreferences.Editor store = getDeviceProtectedSharedPreferences(context).edit();
		final String flat_pkg_ops = list == null || list.isEmpty() ? null : list.stream().filter(ops -> pkg.equals(ops.getPackageName()))
				.flatMap(ops -> ops.getOps().stream()).filter(entry -> ! isDefaultMode(entry)).map(entry -> entry.getOp() + ":" + entry.getMode())
				.collect(Collectors.joining(","));
		if (flat_pkg_ops == null || flat_pkg_ops.isEmpty()) store.remove(pkg).apply();
		else store.putString(pkg, flat_pkg_ops).apply();
		Log.d(TAG, "App ops saved for " + pkg + ": " + flat_pkg_ops);
		return true;
	}

	private static boolean isDefaultMode(final Hacks.AppOpsManager.OpEntry entry) {
		return entry.getMode() == AppOpsCompat.opToDefaultMode(entry.getOp());
	}

	@ProfileUser public static boolean restoreAppOps(final Context context, final String pkg) throws PackageManager.NameNotFoundException {
		final String pkg_ops = getDeviceProtectedSharedPreferences(context).getString(pkg, null);
		if (pkg_ops == null || pkg_ops.isEmpty()) return false;
		final int uid = context.getPackageManager().getPackageUid(pkg, MATCH_DISABLED_COMPONENTS);
		final AppOpsCompat app_ops = new AppOpsCompat(context);
		Arrays.stream(pkg_ops.split(",")).map(pkg_op -> pkg_op.trim().split(":")).filter(splits -> splits.length >= 2).forEach(splits -> {
			final int op = Integer.parseInt(splits[0]);
			final int mode = Integer.parseInt(splits[1].substring(0, 1));
			app_ops.setMode(op, uid, pkg, mode);
		});
		Log.d(TAG, "App ops restored for " + pkg + ": " + pkg_ops);
		return false;
	}

	private static SharedPreferences getDeviceProtectedSharedPreferences(final Context context) {
		return (context.isDeviceProtectedStorage() ? context : context.createDeviceProtectedStorageContext()).getSharedPreferences(PREFS_NAME, 0);
	}

	private static final String TAG = "Island.AOH";
}
