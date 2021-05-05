package com.oasisfeng.island.service

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminService
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.IBinder
import android.os.Looper
import android.os.MessageQueue
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.oasisfeng.island.PersistentService
import com.oasisfeng.island.util.toId

/**
 * Persistent helper service.
 *
 * Created by Oasis on 2019-10-12.
 */
@RequiresApi(O) class IslandPersistentService: DeviceAdminService() {       // TODO: Fallback to foreground service for unmanaged user.

    override fun onCreate() {
        Log.d(TAG, "Initializing persistent services...")
        Looper.getMainLooper().queue.addIdleHandler(mInitializer)
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
        Looper.getMainLooper().queue.removeIdleHandler(mInitializer)
        mConnections.forEach {
            try { unbindService(it) }
            catch (e: RuntimeException) { Log.e(TAG, "Error disconnecting ${it.mComponent}", e) }}
    }

    override fun unbindService(conn: ServiceConnection) = super.unbindService(conn).also {
        if (conn is PersistentServiceConnection) Log.i(TAG, "Stopping persistence service: ${conn.mComponent.flattenToShortString()}") }

    private val mInitializer = MessageQueue.IdleHandler { false.also { bindPersistentServices() } }
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