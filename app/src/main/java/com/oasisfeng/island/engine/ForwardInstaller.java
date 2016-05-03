package com.oasisfeng.island.engine;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

/**
 * Forward the back-installation request to owner user.
 *
 * Created by Oasis on 2016/5/3.
 */
public class ForwardInstaller extends Activity {

	private static final String ACTION_INSTALL_APP = "com.oasisfeng.island.action.INSTALL_APP";

	/** Should be called in managed profile */
	static Intent makeIntent(final String pkg) {
		return new Intent(ACTION_INSTALL_APP, Uri.fromParts("package", pkg, null));
	}

	/** Should be called in managed profile */
	static IntentFilter getIntentFilter() {
		final IntentFilter filter = new IntentFilter(ACTION_INSTALL_APP);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		filter.addDataScheme("package");
		return filter;
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		if (ACTION_INSTALL_APP.equals(intent.getAction()) && intent.getData() != null)
			startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE, intent.getData()));
		finish();
	}
}
