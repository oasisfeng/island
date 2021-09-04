package com.oasisfeng.island.util

import android.os.UserManager

fun UserManager.getProfileIds(userId: Int, enabledOnly: Boolean): IntArray? = Hacks.UserManager_getProfileIds.invoke(userId, enabledOnly).on(this)
