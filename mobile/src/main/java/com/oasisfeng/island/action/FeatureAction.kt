package com.oasisfeng.island.action

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.getSystemService
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.mobile.BuildConfig
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.CallerAwareActivity
import com.oasisfeng.island.util.Users

/**
 * Activity to handle app action "Open Feature"
 *
 * Created by Oasis on 2019-7-1.
 */
private const val URI_HOST = "feature"

class FeatureActionActivity : CallerAwareActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
        finish()
    }

    private fun handleIntent() {
        val data = intent.data; val caller = callingPackage
        if (BuildConfig.DEBUG) Toast.makeText(this, "$data\nfrom: $caller", Toast.LENGTH_LONG).show()
        if (intent.action != Intent.ACTION_VIEW || data?.host != URI_HOST || data.pathSegments.size < 1) return

        val query = data.pathSegments[0]
        AsyncTask.execute {
            findApp(query)?.also { activity ->
                val pkg = activity.componentName.packageName
                Shuttle(this, Users.profile ?: return@also).launch {
                    if (IslandManager.ensureAppFreeToLaunch(this, pkg).isEmpty())
                        IslandManager.launchApp(this, pkg, Users.current()) }
            } ?: Toasts.showLong(this, "Not found: $query")
        }
    }

    private fun findApp(query: String): LauncherActivityInfo? {     // TODO: Support frozen apps in Island
        getSystemService<LauncherApps>()!!.getActivityList(null, Users.profile).also { candidates ->
            if (query.all(Char::isLetterOrDigit))
                candidates.filter { candidate -> candidate.componentName.packageName.contains(query, ignoreCase = true) }.apply {
                    if (size in 1..3) return this[0]        // Not a good query word if more than 3 matches
                }   // TODO: More sophisticated matching
            candidates.firstOrNull { candidate -> candidate.label.contains(query, ignoreCase = true) }?.apply { return this }
        }
        return null
    }
}