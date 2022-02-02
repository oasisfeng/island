package com.oasisfeng.android.provider

import android.app.Activity
import android.content.Intent
import android.os.Bundle

object SettingsUtils {

	@JvmStatic fun launchActivity(activity: Activity, action: String, key: String? = null) =
		activity.startActivity(Intent(action).apply { if (key != null) setHighlightKey(key) })

	@JvmStatic fun Intent.setHighlightKey(key: String) =
		putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })

	private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
	private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
}
