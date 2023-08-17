package com.oasisfeng.island.util

import android.util.Log

@Suppress("NOTHING_TO_INLINE") object UserPrefixedLog {
	inline fun v(tag: String, message: String) { Log.v(tag, prefix + message) }
	inline fun d(tag: String, message: String) { Log.d(tag, prefix + message) }
	inline fun i(tag: String, message: String) { Log.i(tag, prefix + message) }
	inline fun w(tag: String, message: String, t: Throwable? = null) {
		if (t != null) Log.w(tag, prefix + message, t) else Log.w(tag, prefix + message) }
	inline fun e(tag: String, message: String, t: Throwable? = null) {
		if (t != null) Log.e(tag, prefix + message, t) else Log.e(tag, prefix + message) }

	val prefix = "[" + Users.currentId() + "] "
}
