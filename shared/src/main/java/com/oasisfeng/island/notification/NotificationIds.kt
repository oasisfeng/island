package com.oasisfeng.island.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat

fun NotificationIds.post(context: Context, tag: String? = null, build: Notification.Builder.() -> Unit) {
	@Suppress("DEPRECATION") val n = Notification.Builder(context).also(build)
	NotificationManagerCompat.from(context).notify(tag, id(), buildChannel(context, n).build())
}

