package com.oasisfeng.android.content.pm

import android.content.ComponentName
import android.content.pm.ComponentInfo

fun ComponentInfo.getComponentName() = ComponentName(packageName, name)