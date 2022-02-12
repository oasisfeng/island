package com.oasisfeng.island.controller

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.INSTALL_REASON_USER
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Parcel
import android.util.Log
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.island.shuttle.ContextShuttle

class PrivilegedRemoteWorker: Binder() {

    /** This method runs with privileged permission via Shizuku */
    private fun cloneAppViaShizuku(context: Context, pkg: String, userId: Int): Boolean {
        if (SDK_INT >= Q) {
            val profileContext = ContextShuttle.createContextAsUser(context, UserHandles.of(userId)) ?: return false
            profileContext.packageManager.packageInstaller.installExistingPackage(pkg, INSTALL_REASON_USER, null)
            return true
        } else try {   // int installExistingPackageAsUser(String packageName, int userId) throws NameNotFoundException
            val result = PackageManager::class.java.getMethod("installExistingPackageAsUser", String::class.java, Int::class.java)
                .invoke(context.packageManager, pkg, userId)
            return result == INSTALL_SUCCEEDED
        } catch (e: PackageManager.NameNotFoundException) { return false }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        val pkg = data.readString()!!
        val userId = data.readInt()
        try {
            val result = cloneAppViaShizuku(getSystemContext(), pkg, userId)
            reply?.writeInt(if (result) 1 else 0) }
        catch (e: Exception) {
            Log.e(TAG, "Error cloning $pkg via Shizuku", e)
            reply?.writeInt(-1) }
        return true
    }

    private fun getSystemContext(): Context {
        try {
            val classActivityThread = Class.forName("android.app.ActivityThread")
            val at = classActivityThread.getMethod("currentActivityThread").invoke(null)
            val context = classActivityThread.getMethod("getSystemContext").invoke(at) as? Context
            if (context != null) return context }
        catch (e: ReflectiveOperationException) {
            Log.e(TAG, "Error retrieving system context", e) }
        throw UnsupportedOperationException()
    }

    init { Log.i(TAG, "Running in Shizuku...") }

    companion object {
        private const val INSTALL_SUCCEEDED = 1     // PackageManager.INSTALL_SUCCEEDED
        private const val TAG = "Island.PRW"
    }
}
