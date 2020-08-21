package com.oasisfeng.island.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.CheckResult
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.HitBuilders.EventBuilder
import com.google.android.gms.analytics.Tracker
import com.google.firebase.analytics.FirebaseAnalytics
import com.oasisfeng.island.firebase.FirebaseWrapper
import com.oasisfeng.island.shared.BuildConfig
import com.oasisfeng.island.shared.R
import org.intellij.lang.annotations.Pattern

/**
 * The analytics implementation in local process
 *
 * Created by Oasis on 2017/3/23.
 */
internal class AnalyticsImpl(context: Context) : Analytics {

	@CheckResult override fun event(@Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") event: String): Analytics.Event {
		val bundle = Bundle()
		return object : Analytics.Event {
			@CheckResult override fun withRaw(key: String, value: String?) = this.also { bundle.putString(key, value ?: return@also) }
			override fun send() = reportEvent(event, bundle) }
	}

	override fun trace(key: String, value: String): Analytics = this.also { CrashReport.setProperty(key, value) }
	override fun trace(key: String, value: Int): Analytics = this.also { CrashReport.setProperty(key, value) }
	override fun trace(key: String, value: Boolean): Analytics = this.also { CrashReport.setProperty(key, value) }

	override fun report(t: Throwable) = CrashReport.logException(t)
	override fun report(message: String, t: Throwable) { CrashReport.log(message); CrashReport.logException(t) }

	override fun reportEvent(event: String, params: Bundle) {
		Log.d(TAG, if (params.isEmpty) "Event: $event" else "Event: $event $params")
		val builder = EventBuilder().setCategory(event)
		val category = params.getString(Analytics.Param.ITEM_CATEGORY.key)
		val id = params.getString(Analytics.Param.ITEM_ID.key)
		val name = params.getString(Analytics.Param.ITEM_NAME.key)
		if (category != null) {
			builder.setAction(category)
			require(! BuildConfig.DEBUG || name == null) { "Category and Name cannot be used at the same time: $event" }
		} else if (name != null) builder.setAction(name)
		if (id != null) builder.setLabel(id)
		mGoogleAnalytics.send(builder.build())
		mFirebaseAnalytics.logEvent(event, params)
		CrashReport.log("Event: $event ${params.toString().substring(6/* Remove leading "Bundle" */)}")
	}

	override fun setProperty(property: Analytics.Property, value: String) {
		mGoogleAnalytics["&cd" + property.ordinal + 1] = value // Custom dimension (index >= 1)
		mFirebaseAnalytics.setUserProperty(property.key, value)
		CrashReport.setProperty(property.key, value)
	}

	private val mGoogleAnalytics: Tracker
	private val mFirebaseAnalytics: FirebaseAnalytics

	init {  // TODO: De-dup the user identity between Mainland and Island.
		val googleAnalytics = GoogleAnalytics.getInstance(context)
		if (BuildConfig.DEBUG) googleAnalytics.setDryRun(true)
		mGoogleAnalytics = googleAnalytics.newTracker(R.xml.analytics_tracker)
		mGoogleAnalytics.enableAdvertisingIdCollection(true)
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(FirebaseWrapper.init())
	}
}

private const val TAG = "Analytics"
