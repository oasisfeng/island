package com.oasisfeng.common.app

import android.content.pm.ApplicationInfo
import com.oasisfeng.island.util.Hacks

fun ApplicationInfo.hasRequestedLegacyExternalStorage(): Boolean {
	return privateFlags and PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE != 0
}

val ApplicationInfo.privateFlags: Int; get() = Hacks.ApplicationInfo_privateFlags.get(this)

private const val PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE = 1 shl 29
