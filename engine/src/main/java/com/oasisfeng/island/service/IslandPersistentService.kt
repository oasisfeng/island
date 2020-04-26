package com.oasisfeng.island.service

import android.app.AppOpsManager
import android.app.admin.DeviceAdminService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build.VERSION_CODES.O
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.oasisfeng.island.appops.AppOpsCompat
import com.oasisfeng.island.util.Users

/**
 * Persistent helper service.
 *
 * Created by Oasis on 2019-10-12.
 */
@RequiresApi(O) class IslandPersistentService: DeviceAdminService() {

    override fun onCreate() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return      // TODO: Remove before release

        Toast.makeText(this@IslandPersistentService, "Starting PService in user ${Users.toId(Process.myUserHandle())}...", Toast.LENGTH_LONG).show()
        getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsCompat.OPSTR_REQUEST_INSTALL_PACKAGES, null, mListener)
        registerReceiver(mPackageRestartQueryReceiver, IntentFilter(ACTION_QUERY_PACKAGE_RESTART).apply { addDataScheme("package") })
    }

    private val mPackageRestartQueryReceiver: BroadcastReceiver = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val pkgs = intent.getStringArrayExtra(EXTRA_PACKAGES) ?: return
        if (pkgs.size == 1 && pkgs[0] == pkg) Toast.makeText(context, "QUERY_PACKAGE_RESTART: $pkg", Toast.LENGTH_LONG).show()
    }}

    override fun onDestroy() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return      // TODO: Remove before release
        try { unregisterReceiver(mPackageRestartQueryReceiver) } catch (e: IllegalArgumentException) {}
        getSystemService(AppOpsManager::class.java).stopWatchingMode(mListener)
    }

    private val mHandler = Handler(Looper.getMainLooper())
    private val mListener = AppOpsManager.OnOpChangedListener { op, pkg ->
        mHandler.post { Toast.makeText(this@IslandPersistentService, "Changed: $op - $pkg", Toast.LENGTH_LONG).show() }}
}

private const val ACTION_QUERY_PACKAGE_RESTART = "android.intent.action.QUERY_PACKAGE_RESTART"  // Intent.ACTION_QUERY_PACKAGE_RESTART
private const val EXTRA_PACKAGES = "android.intent.extra.PACKAGES"                              // Intent.EXTRA_PACKAGES

