package com.oasisfeng.island.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

class ModuleContext(val context: Context) {

	fun forDeclaredPermission(permission: String): Context? {
		val pm = context.packageManager
		return pm.getPackagesForUid(Process.myUid())?.firstOrNull { pkg -> @Suppress("DEPRECATION")
			pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)?.requestedPermissions?.contains(permission) == true
		}?.let { pkg -> try { context.createPackageContext(pkg, 0) } catch (e: Exception) { null }}
	}
}