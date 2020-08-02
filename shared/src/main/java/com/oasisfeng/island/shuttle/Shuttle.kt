package com.oasisfeng.island.shuttle

import android.app.PendingIntent
import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Shuttle(private val context: Context, private val to: UserHandle) {

	fun launch(at: CoroutineScope, function: Context.() -> Unit) {
		if (to == Users.current()) function(context) else at.launch { shuttle(function) }}

	suspend fun <R> invoke(function: Context.() -> R) = if (to == Users.current()) context.function() else shuttle(function)

	/* Helpers to avoid redundant local variables. ("inline" is used to ensure only "Context.() -> R" function is shuttled) */
	inline fun <A> launch(at: CoroutineScope, with: A, crossinline function: Context.(A) -> Unit) = launch(at) { function(with) }
	suspend inline fun <A, R> invoke(with: A, crossinline function: Context.(A) -> R) = invoke { this.function(with) }

	private suspend fun <R> shuttle(function: Context.() -> R): R {
		val shuttle = PendingIntentShuttle.load(context, to)
				?: return PendingIntentShuttle.sendToProfileAndShuttle(context, to, function)
		return try { PendingIntentShuttle.shuttle(context, shuttle, function) }
		catch (e: PendingIntent.CanceledException) {
			Log.w(TAG, "Old shuttle (${Users.current()} to ${to.toId()}) is broken, resend now.")
			PendingIntentShuttle.sendToProfileAndShuttle(context, to, function) }
	}
}

private const val TAG = "Island.Shuttle"