package com.oasisfeng.island.shuttle

import android.content.Context
import android.os.UserHandle
import com.oasisfeng.island.util.Users

class Shuttle(val context: Context, val to: UserHandle) {

	/** @return Job if launched in coroutine, otherwise null. */
	fun launch(function: Context.() -> Unit) =
			if (to == Users.current()) { function(context) } else shuttle(function)
	fun launchNoThrows(function: Context.() -> Unit): Boolean =
			if (to == Users.current()) { function(context); true } else shuttleNoThrows(function)
	fun <R> invoke(function: Context.() -> R) =
			if (to == Users.current()) context.function() else shuttle(function)

	/* Helpers to avoid redundant local variables. ("inline" is used to ensure only "Context.() -> R" function is shuttled) */
	inline fun <A> launch(with: A, crossinline function: Context.(A) -> Unit) { launch { function(with) }}
	inline fun <A, R> invoke(with: A, crossinline function: Context.(A) -> R)
			= invoke { this.function(with) }

	private fun <R> shuttle(function: Context.() -> R): R {
		val result = ShuttleProvider.call(context, to, function)
		if (result.isNotReady()) throw IllegalStateException("Shuttle not ready")
		return result.get()
	}

	private fun shuttleNoThrows(function: Context.() -> Unit): Boolean {
		val result = ShuttleProvider.call(context, to, function)
		return ! result.isNotReady()
	}
}
