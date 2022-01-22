package com.oasisfeng.android.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

inline fun receiver(crossinline onReceive: Context.(Intent) -> Unit) = object: BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) = onReceive.invoke(context, intent)
}

suspend fun waitForBroadcast(context: Context, action: String, timeout: Long, block: (Continuation<Intent?>) -> Unit) =
	waitForBroadcast(context, IntentFilter(action), timeout, block)

suspend fun waitForBroadcast(context: Context, filter: IntentFilter, timeout: Long, block: (Continuation<Intent?>) -> Unit) =
	coroutineScope<Intent?> {
		var watcher: BroadcastReceiver? = null
		try {
			suspendCoroutineWithTimeout(timeout) { continuation ->
				watcher = receiver { continuation.resume(it) }
				context.registerReceiver(watcher, filter.apply { priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1 })
				block(continuation) }}
		finally {
			context.unregisterReceiver(watcher) }}

fun sendProtectedBroadcastInternally(context: Context, intent: Intent) {
	val myUid = Process.myUid()
	context.packageManager.queryBroadcastReceivers(intent, 0).filter { it.activityInfo.applicationInfo.uid == myUid }.forEach {
		try {
			val clazz = Class.forName(it.activityInfo.name)
			if (BroadcastReceiver::class.java.isAssignableFrom(clazz))
				(clazz.newInstance() as BroadcastReceiver).onReceive(context, intent)
		} catch (e: ClassNotFoundException) { Log.e(TAG, "Error sending internal broadcast", e) }}
}

private suspend inline fun <T> suspendCoroutineWithTimeout(timeout: Long, crossinline block: (Continuation<T>) -> Unit) =
	withTimeout(timeout) { suspendCancellableCoroutine(block = block) }

private const val TAG = "Island.Broadcast"