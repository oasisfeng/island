package com.oasisfeng.island.shuttle

import android.app.PendingIntent
import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.oasisfeng.island.shuttle.PendingIntentShuttle.ProfileUnlockCanceledException
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch

class Shuttle(private val context: Context, private val to: UserHandle) {

	/** @return Job if launched in coroutine, otherwise null. */
	fun launch(at: CoroutineScope, alwaysByActivity: Boolean = false, function: Context.() -> Unit)
			= if (to == Users.current()) { function(context); null } else at.launch(Dispatchers.Unconfined) {
			try { shuttle(function, alwaysByActivity) }
			catch (e: ProfileUnlockCanceledException) { Log.i(TAG, "Profile unlock is canceled.") }}

	fun launchAsFuture(function: Context.() -> Unit) = launch(at = GlobalScope, function = function)?.asCompletableFuture()

	@Throws(ProfileUnlockCanceledException::class)
	suspend fun <R> invoke(alwaysByActivity: Boolean = false, function: Context.() -> R)
			= if (to == Users.current()) context.function() else shuttle(function, alwaysByActivity)

	/* Helpers to avoid redundant local variables. ("inline" is used to ensure only "Context.() -> R" function is shuttled) */
	inline fun <A> launch(at: CoroutineScope, with: A, crossinline function: Context.(A) -> Unit) = launch(at) { function(with) }
	suspend inline fun <A, R> invoke(with: A, alwaysByActivity: Boolean = false, crossinline function: Context.(A) -> R)
			= invoke(alwaysByActivity = alwaysByActivity) { this.function(with) }

	/** @param alwaysByActivity always shuttle by activity launch. It ensures activity launch in shuttled function is not blocked by background activity launch restrictions. (vital on most Chinese ROMs) */
	private suspend fun <R> shuttle(function: Context.() -> R, alwaysByActivity: Boolean): R {
		val shuttle = (if (alwaysByActivity) null else PendingIntentShuttle.retrieveShuttle(context, to))
				?: return PendingIntentShuttle.sendToProfileAndShuttle(context, to, function)
		return try { PendingIntentShuttle.shuttle(context, shuttle, function) }
		catch (e: PendingIntent.CanceledException) {
			Log.w(TAG, "Old shuttle (${Users.current()} to ${to.toId()}) is broken, resend now.")
			PendingIntentShuttle.sendToProfileAndShuttle(context, to, function) }
	}
}

private const val TAG = "Island.Shuttle"