package com.oasisfeng.island.service

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminService
import android.content.*
import android.content.Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.ServiceInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.oasisfeng.android.content.pm.getComponentName
import com.oasisfeng.island.PersistentService
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.util.toId

/**
 * Persistent helper service.
 *
 * Created by Oasis on 2019-10-12.
 */
@RequiresApi(O) class IslandPersistentService: DeviceAdminService() {       // TODO: Fallback to foreground service for unmanaged user.

    override fun onCreate() {
        Log.d(TAG, "Initializing persistent services...")
        registerReceiver(mComponentStateReceiver, IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") })
        Looper.getMainLooper().queue.addIdleHandler { false.also { bindPersistentServices() }}
    }

    private fun bindPersistentServices(pkg: String? = null) {
        val intent = Intent(PersistentService.SERVICE_INTERFACE).setPackage(pkg); val uid = Process.myUid()
        val candidates = packageManager.queryIntentServices(intent, 0)
        candidates.forEach { if (it.serviceInfo.applicationInfo.uid == uid) bindPersistentService(it.serviceInfo) }
    }

    private fun bindPersistentService(service: ServiceInfo) {
        val component = ComponentName(service.packageName, service.name)
        val componentName = component.flattenToShortString()
        Log.i(TAG, "Starting persistent service: $componentName in user ${Process.myUserHandle().toId()}")

        val connection = PersistentServiceConnection(component)
        mConnections.add(connection)
        @SuppressLint("WrongConstant") val result = bindService(Intent(PersistentService.SERVICE_INTERFACE).setComponent(component),
                connection, BIND_AUTO_CREATE or BIND_NOT_FOREGROUND or (if (SDK_INT >= Q) BIND_INCLUDE_CAPABILITIES else 0))
        if (! result) Log.e(TAG, "Failed to start persistent service: $componentName")
    }

    override fun onDestroy() {
        unregisterReceiver(mComponentStateReceiver)
        mConnections.forEach {
            try { unbindService(it) }
            catch (e: RuntimeException) { Log.e(TAG, "Error disconnecting ${it.mComponent}", e) }}
    }

    private val mComponentStateReceiver = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(Intent.EXTRA_UID, 0) != Process.myUid()) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val components = intent.getStringArrayExtra(EXTRA_CHANGED_COMPONENT_NAME_LIST)?.takeIf { it.isNotEmpty() } ?: return
        val pm = context.packageManager
        if (components[0] == pkg) {     // Package level change
            if (try { pm.getApplicationInfo(pkg, 0).enabled } catch (e: PackageManager.NameNotFoundException) { false })
                bindPersistentServices(pkg)
            else unbindPersistentServices(pkg)
        } else components.forEach { className ->
            val component = ComponentName(pkg, className)
            val info = try { pm.getServiceInfo(component, MATCH_DISABLED_COMPONENTS or MATCH_UNINSTALLED_PACKAGES) }
            catch (e: PackageManager.NameNotFoundException) { return@forEach }  // Non-service component
            if (info.isEnabled && ! info.applicationInfo.hidden) {
                if (mConnections.none { it.mComponent == component }) bindPersistentService(info) }
            else unbindPersistentService(info.getComponentName()) }
    }}

    private fun unbindPersistentService(component: ComponentName) {
        mConnections.removeIf { (it.mComponent == component).also { matched -> if (matched) { unbindService(it) }}}
    }

    private fun unbindPersistentServices(pkg: String) {
        mConnections.filter { it.mComponent.packageName == pkg }.apply {
            forEach { unbindService(it) }
            mConnections.removeAll(this) }
    }

    override fun unbindService(conn: ServiceConnection) = super.unbindService(conn).also {
        if (conn is PersistentServiceConnection) Log.i(TAG, "Stopping persistence service: ${conn.mComponent.flattenToShortString()}") }

    private val mConnections = ArrayList<PersistentServiceConnection>()

    private inner class PersistentServiceConnection(val mComponent: ComponentName) : ServiceConnection {
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            Log.i(TAG, "Connected: ${component.flattenToShortString()}") }
        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Disconnected: ${name.flattenToShortString()}") }
        override fun onNullBinding(name: ComponentName) {
            Log.i(TAG, "Quited: ${name.flattenToShortString()}")
            mConnections.remove(this)
            unbindService(this) }
    }
}

private const val TAG = "Island.PS"