package com.oasisfeng.island.shuttle

import android.content.Context
import android.os.UserHandle
import android.widget.Toast
import com.oasisfeng.island.shared.BuildConfig
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class Shuttle(private val context: Context, private val to: UserHandle) {

	fun launch(at: CoroutineScope, function: Context.() -> Unit) {
		if (to == Users.current()) function(context) else at.launch { shuttle(function) }}

	fun <A, R> future(at: CoroutineScope, a: A, function: Context.(A) -> R): CompletableFuture<R>
			= if (to == Users.current()) CompletableFuture.completedFuture(function(context, a))
			else at.future { shuttle { function(a) }}

	suspend fun <R> invoke(function: Context.() -> R) = shuttleIfNeeded(function)
	suspend fun <A, R> invoke(a: A, function: Context.(A) -> R) = shuttleIfNeeded { function(a) }

	private suspend fun <R> shuttleIfNeeded(function: Context.() -> R)
			= if (to == Users.current()) function(context) else shuttle(function)

	private suspend fun <R> shuttle(function: Context.() -> R): R {
		val shuttle = PendingIntentShuttle.load(context, to)
		if (shuttle != null) return PendingIntentShuttle.shuttle(context, shuttle, function)

		if (to == Users.profile) return suspendCoroutine { continuation ->     // Fallback to method shuttle
			if (BuildConfig.DEBUG) Toast.makeText(context, "Fallback to M shuttle", Toast.LENGTH_LONG).show()
			MethodShuttle.runInProfile(context, function).whenComplete { r, t ->
				if (t != null) continuation.resumeWithException(t)
				else continuation.resume(r) }}
		throw IllegalStateException("Shuttle to profile ${to.toId()} is unavailable")
	}
}
