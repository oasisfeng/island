package com.oasisfeng.island.shuttle;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;

/**
 * Keep the process from being destroyed when service shuttle is working
 *
 * Created by Oasis on 2016/10/19.
 */
public class ShuttleKeeper extends Service {

	@Nullable @Override public IBinder onBind(final Intent intent) { return null; }

	static void onServiceConnected(final Context context) {
		context.startService(new Intent(context, ShuttleKeeper.class));
	}

	static void onServiceUnbind(final Context context, final Intent intent) {
		if (DUMMY_RECEIVER.peekService(context, intent) != null) return;	// Fast check for common cases

		final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		final List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
		final String pkg = intent.getPackage() != null ? intent.getPackage() : context.getPackageName();
		for (final ActivityManager.RunningServiceInfo service : services) {
			if (! pkg.equals(service.service.getPackageName())) continue;
			if (service.clientCount != 0) return;		// Service is still bound
		}
		context.stopService(new Intent(context, ShuttleKeeper.class));
	}

	@Override public int onStartCommand(final Intent intent, final int flags, final int startId) {
		return START_NOT_STICKY;		// STICKY has no point here, since we share process with caller.
	}

	@Override public void onCreate() { Log.d(TAG, "Start"); }
	@Override public void onDestroy() { Log.d(TAG, "Stop"); }

	private static final BroadcastReceiver DUMMY_RECEIVER = new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent i) {}};
	private static final String TAG = "ShuttleKeeper";
}
