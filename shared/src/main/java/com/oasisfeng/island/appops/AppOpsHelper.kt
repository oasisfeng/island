package com.oasisfeng.island.appops

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser

/**
 * Keep app-ops settings in [SharedPreferences], which can later be restored when app is unfrozen.
 *
 * Created by Oasis on 2019-3-1.
 */
private const val PREFS_NAME = "app_ops"

@RequiresApi(28) object AppOpsHelper {

    @ProfileUser @OwnerUser @RequiresPermission(GET_APP_OPS_STATS) @JvmStatic @Throws(PackageManager.NameNotFoundException::class)
    fun saveAppOps(context: Context, pkg: String) {
        val store = getDeviceProtectedSharedPreferences(context)    // As early as possible since the async loading takes time.
        val uid = context.packageManager.getPackageUid(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        val list = AppOpsCompat(context).getOpsForPackage(uid, pkg, null)
        val flatPkgOps = if (list == null || list.isEmpty()) null else list.asSequence().filter { ops -> pkg == ops.packageName }
                    .flatMap { ops -> ops.ops.asSequence() }.filter { entry -> entry.mode != AppOpsCompat.opToDefaultMode(entry.op) }
                    .map { entry -> "${entry.op}:${entry.mode}" }.joinToString(",")
        if (flatPkgOps?.isNotEmpty() == true) {
            store.edit().putString(pkg, flatPkgOps).apply()
            Log.d(TAG, "App-ops saved for $pkg: $flatPkgOps")
        } else store.edit().remove(pkg).apply()
    }

    @ProfileUser @Throws(PackageManager.NameNotFoundException::class) @JvmStatic
    fun restoreAppOps(context: Context, pkg: String): Boolean {
        val pkgOps = getDeviceProtectedSharedPreferences(context).getString(pkg, null)
        if (pkgOps == null || pkgOps.isEmpty()) return false
        val uid = context.packageManager.getPackageUid(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        val ops = AppOpsCompat(context)
        pkgOps.split(",").asSequence().map { pkg_op -> pkg_op.trim().split(":")}.filter { splits -> splits.size >= 2 }.forEach { splits ->
            val op = Integer.parseInt(splits[0]); val mode = Integer.parseInt(splits[1].substring(0, 1))
            ops.setMode(op, uid, pkg, mode)
        }
        Log.d(TAG, "App-ops restored for $pkg: $pkgOps")
        return true
    }

    private fun getDeviceProtectedSharedPreferences(context: Context): SharedPreferences {
        return (if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()).getSharedPreferences(PREFS_NAME, 0)
    }
}

private const val TAG = "Island.AOH"
