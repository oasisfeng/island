package com.oasisfeng.island.firebase;

import android.app.Application;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.condom.CondomProcess;
import com.oasisfeng.pattern.PseudoContentProvider;

/**
 * Condom installer for separate process of Firebase Analytics
 *
 * Created by Oasis on 2018/1/7.
 */
public abstract class FirebaseCondom extends PseudoContentProvider {

	@Override public boolean onCreate() {
		final int gms_availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context());
		if (gms_availability != ConnectionResult.SUCCESS) {
			Log.e("Condom.FA", "Installing condom process");
			CondomProcess.installInCurrentProcess((Application) context().getApplicationContext(), "FA", buildOptions());
		}
		return false;
	}

	static CondomOptions buildOptions() {
		return new CondomOptions().setOutboundJudge((t, i, pkg) -> ! "com.google.android.gms".equals(pkg));
	}
}
