package com.oasisfeng.island.analytics

import android.os.Bundle
import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.Size
import com.google.firebase.analytics.FirebaseAnalytics
import com.oasisfeng.island.IslandApplication
import org.intellij.lang.annotations.Pattern

/**
 * Abstraction for analytics service
 *
 * Created by Oasis on 2016/5/26.
 */
interface Analytics {

	interface Event {
		@CheckResult fun with(key: Param, value: String?) = withRaw(key.key, value)
		@CheckResult fun withRaw(key: String, value: String?): Event
		fun send()
	}

	enum class Param(@param:Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") val key: String) {
		ITEM_ID(FirebaseAnalytics.Param.ITEM_ID),
		/** ITEM_CATEGORY and ITEM_NAME cannot be used together (limitation in Google Analytics implementation)  */
		ITEM_NAME(FirebaseAnalytics.Param.ITEM_NAME),
		ITEM_CATEGORY(FirebaseAnalytics.Param.ITEM_CATEGORY),
		LOCATION(FirebaseAnalytics.Param.LOCATION),
		CONTENT(FirebaseAnalytics.Param.CONTENT);
	}

	@CheckResult fun event(@Size(min = 1, max = 40) @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") event: String): Event
	fun reportEvent(event: String, params: Bundle)
	fun trace(key: String, value: String): Analytics
	fun trace(key: String, value: Int): Analytics
	fun trace(key: String, value: Boolean): Analytics
	fun report(t: Throwable)
	fun report(message: String, t: Throwable)
	fun logAndReport(tag: String, message: String, t: Throwable) { Log.e(tag, message, t); report(message, t) }

	enum class Property(val key: String) {
		DeviceOwner("device_owner"),
		IslandSetup("island_setup"),
		EncryptionRequired("encryption_required"),
		DeviceEncrypted("device_encrypted"),
		RemoteConfigAvailable("remote_config_avail"),
		FileShuttleEnabled("file_shuttle_enabled");

	}

	fun setProperty(property: Property, @Size(max = 36) value: String)
	fun setProperty(property: Property, value: Boolean): Analytics = this.also { setProperty(property, value.toString()) }

	companion object {
		@JvmStatic fun log(tag: String, message: String) { Log.i(tag, message); CrashReport.log("[$tag] $message") }
		@JvmStatic @Suppress("FunctionName") fun `$`() = impl
		operator fun invoke() = impl
		private val impl: Analytics = AnalyticsImpl(IslandApplication.`$`())
	}
}

fun analytics(): Analytics = Analytics()
