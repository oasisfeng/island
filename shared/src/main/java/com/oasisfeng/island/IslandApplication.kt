package com.oasisfeng.island

import android.app.Application
import com.oasisfeng.island.analytics.CrashReport

/**
 * For singleton instance purpose only.
 *
 * Created by Oasis on 2018/1/3.
 */
class IslandApplication : Application() {

	companion object {
		@Suppress("FunctionName") @JvmStatic fun `$`(): Application = sInstance

		lateinit var sInstance: IslandApplication
	}

	init {
		sInstance = this
		CrashReport.initCrashHandler()
	}
}