package com.oasisfeng.android.content;

import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION_CODES.O;

/**
 * Created by Oasis on 2018-11-27.
 */
public class IntentCompat {

	public static final String ACTION_SHOW_APP_INFO = "android.intent.action.SHOW_APP_INFO";
	public static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";
	@RequiresApi(O) public static final String EXTRA_AUTO_LAUNCH_SINGLE_CHOICE = "android.intent.extra.AUTO_LAUNCH_SINGLE_CHOICE";
}
