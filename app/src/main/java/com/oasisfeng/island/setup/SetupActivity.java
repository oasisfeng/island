package com.oasisfeng.island.setup;

import android.app.Activity;
import android.os.Bundle;

import com.android.setupwizardlib.util.SystemBarHelper;
import com.oasisfeng.island.R;

/**
 * Island setup wizard
 *
 * Created by Oasis on 2016/9/13.
 */
public class SetupActivity extends Activity {

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		SystemBarHelper.hideSystemBars(getWindow());
		setContentView(R.layout.activity_main);
		getFragmentManager().beginTransaction().replace(R.id.container, new SetupWizardFragment()).commit();
	}
}
