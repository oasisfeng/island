package com.oasisfeng.island.notification

import android.app.Notification
import android.app.Service
import android.content.Context

fun NotificationIds.post(context: Context, tag: String? = null, build: Notification.Builder.() -> Unit) =
		post(context, tag, @Suppress("DEPRECATION") Notification.Builder(context).also(build))

fun NotificationIds.startForeground(service: Service, build: Notification.Builder.() -> Unit) =
		startForeground(service, @Suppress("DEPRECATION") Notification.Builder(service).also(build))
