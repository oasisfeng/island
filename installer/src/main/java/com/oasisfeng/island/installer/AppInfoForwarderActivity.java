package com.oasisfeng.island.installer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.oasisfeng.android.content.IntentCompat;

import androidx.annotation.Nullable;

import static android.content.Intent.EXTRA_USER;
import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.os.Process.myUserHandle;

/**
 * Created by Oasis on 2018-11-16.
 */
public class AppInfoForwarderActivity extends Activity {

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent().putExtra(EXTRA_USER, myUserHandle()).addFlags(FLAG_ACTIVITY_FORWARD_RESULT).setComponent(null).setPackage(null);
		startActivity(Intent.createChooser(intent, getString(R.string.app_info_forwarder_title)).putExtra(IntentCompat.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false));
		finish();
	}
}
