package com.oasisfeng.island.notification

import android.app.Notification
import android.content.Context

fun NotificationIds.post(context: Context, tag: String? = null, build: Notification.Builder.() -> Unit) =
		post(context, tag, @Suppress("DEPRECATION") Notification.Builder(context).also(build))

