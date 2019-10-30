package com.oasisfeng.island.appops

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS
import com.oasisfeng.island.util.*
import com.oasisfeng.island.util.Hacks.AppOpsManager.OpEntry
import com.oasisfeng.island.util.Hacks.AppOpsManager.PackageOps

/**
 * Hybrid implementation for App Ops, with local storage for ops if GET_APP_OPS_STATS is not granted.
 * The local storage also served as a backup upon app freezing, to restore the ops later when app is unfrozen.
 *
 * BEWARE: The local storage implementation cannot track ops changes outside Island (except for open API).
 *         Many ops (e.g. OP_SYSTEM_ALERT_WINDOW, OP_RUN_ANY_IN_BACKGROUND) are known to be toggleable in system Settings.
 *
 * Created by Oasis on 2019-3-1.
 */
private const val PREFS_NAME = "app_ops"

@RequiresApi(28) class AppOpsHelper(private val context: Context) {

    fun setMode(pkg: String, op: Int, mode: Int, uid: Int = getPackageUid(pkg)) {
        if (DevicePolicies(context).invoke(DevicePolicyManager::isApplicationHidden, pkg))
            saveAppOp(pkg, op, mode, uid)    // Ops cannot be set if app is hidden, thus postpone to the next unfreezing.
        else mAppOps.setMode(op, uid, pkg, mode)
    }

    @ProfileUser @OwnerUser @RequiresPermission(GET_APP_OPS_STATS) @Throws(NameNotFoundException::class)
    fun saveAppOps(pkg: String) {
        val uid = context.packageManager.getPackageUid(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        val pkgOps = getOpsForPackage(uid, pkg)
        if (pkgOps == null) { Log.w(TAG, "No ops for $pkg (uid: $uid)"); return }
        saveAppOps(pkgOps)
    }

    private fun saveAppOps(pkgOps: PackageOps) {
        val flatPkgOps = flattenPackageOps(mAppOps, pkgOps.ops)
        val pkg = pkgOps.packageName
        mStore.edit().apply { if (flatPkgOps.isNotEmpty()) putString(pkg, flatPkgOps) else remove(pkg) }.apply()
        Log.d(TAG, "Ops saved for $pkg: $flatPkgOps")
    }

    /** Unlike [Hacks.AppOpsManager.getPackagesForOps], this only returns packages in current user, and may contain packages already uninstalled. */
    fun getPackageOps(op: Int): List<PackageOps> {
        val saved: ArrayList<PackageOps> = mStore.all.mapNotNullTo(ArrayList()) {
            PackageOpsData(it.key, -1, unflattenPackageOps(mAppOps, it.value as? String ?: return@mapNotNullTo null).toList()) }
        if (Permissions.has(context, GET_APP_OPS_STATS)) {      // With permission granted, replace unfrozen packages with actual ops data.
            mAppOps.getPackagesForOps(intArrayOf(op))?.forEach {
                if (UserHandles.getUserId(it.uid) == UserHandles.MY_USER_ID) saved.add(it) }}
        return saved
    }

    private fun getOpsForPackage(uid: Int, pkg: String): PackageOps? {
        if (Permissions.has(context, GET_APP_OPS_STATS))
            return mAppOps.getOpsForPackage(uid, pkg, null).let { if (it.isNullOrEmpty()) null else it[0] }     // At most one element in list
        val flat = mStore.getString(pkg, null)
        if (flat.isNullOrEmpty()) return null
        return PackageOpsData(pkg, uid, unflattenPackageOps(mAppOps, flat).toList())
    }

    @ProfileUser @OwnerUser @Throws(NameNotFoundException::class)
    fun restoreAppOps(pkg: String): Boolean {
        val flatPkgOps = mStore.getString(pkg, null)
        if (flatPkgOps.isNullOrEmpty()) return false

        val uid = context.packageManager.getPackageUid(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        unflattenPackageOps(mAppOps, flatPkgOps).forEach {
            mAppOps.setMode(it.op, uid, pkg, it.mode)
            Log.i(TAG, "App-op restored for $pkg: ${it.op} -> mode ${it.mode}")
        }
        return true
    }

    private fun removeAppOps(pkg: String) = mStore.edit().remove(pkg).apply()
    private fun getPackageUid(pkg: String) = try { context.packageManager.getPackageUid(pkg, MATCH_UNINSTALLED_PACKAGES) } catch (e: NameNotFoundException) { 0 }

    private val mStore = (if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()).getSharedPreferences(PREFS_NAME, 0)    // No lazy, since the async loading takes time.
    private val mAppOpsManager = context.getSystemService(AppOpsManager::class.java) !!
    private val mAppOps by lazy { AppOpsCompat(mAppOpsManager) }

    companion object {

        @JvmStatic fun onOpChanged(context: Context, pkg: String, op: Int, mode: Int) {
            if (Permissions.has(context, GET_APP_OPS_STATS)) return     // No need to track the change if we have permission to query.
            AppOpsHelper(context).saveAppOp(pkg, op, mode)
        }

        internal fun flattenPackageOps(appops: AppOpsCompat, ops: List<OpEntry>): String {
            return ops.asSequence().filter { it.mode != appops.opToDefaultMode(it.op) }.map { entry -> "${entry.op}:${entry.mode}" }.joinToString(",")
        }

        internal fun unflattenPackageOps(appops: AppOpsCompat, flat: String): Sequence<OpEntry> = try {
            flat.splitToSequence(",").map { it.trim().split(":") }.filter { it.size >= 2 }.map{ OpEntryData(it[0].toInt(), it[1].substring(0, 1).toInt()) }
                    .filter { it.mode != appops.opToDefaultMode(it.op) }    // In case data is flatten by older version
        } catch (e: NumberFormatException) { Log.w(TAG, "Drop invalid flatten package ops: $flat"); emptySequence() }
    }

    private fun saveAppOp(pkg: String, op: Int, mode: Int, uid: Int = getPackageUid(pkg)) {
        val existent = getOpsForPackage(uid, pkg)
        Log.i(TAG, "Before ")
        val ops = if (existent != null) ArrayList(existent.ops) else ArrayList(1)
        ops.removeIf { it.op == op }    // May not be mutable OpEntryData, just replace it.
        ops.add(OpEntryData(op, mode))
        saveAppOps(PackageOpsData(pkg, uid, ops))
    }

    data class PackageOpsData(private val packageName: String, private val uid: Int, private val ops: List<OpEntry>): PackageOps {
        override fun getPackageName() = packageName
        override fun getUid() = uid
        override fun getOps() = ops
    }

    data class OpEntryData(private val op: Int, private val mode: Int): OpEntry {
        override fun getOp() = op
        override fun getMode() = mode
    }

    class PackageFullRemovalReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
            AppOpsHelper(context).removeAppOps(intent.data?.schemeSpecificPart ?: return)
        }
    }
}

private const val TAG = "Island.AOH"
