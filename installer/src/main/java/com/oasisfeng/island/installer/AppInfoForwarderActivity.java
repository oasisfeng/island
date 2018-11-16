package com.oasisfeng.island.installer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

/**
 * Created by Oasis on 2018-11-16.
 */
public class AppInfoForwarderActivity extends Activity {

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivity(Intent.createChooser(getIntent().setComponent(null).setPackage(null).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT),
				getString(R.string.app_info_forwarder_title)));
		finish();
	}
}
