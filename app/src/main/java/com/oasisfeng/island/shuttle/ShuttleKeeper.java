package com.oasisfeng.island.shuttle;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.island.R;
import com.oasisfeng.island.notification.NotificationIds;

import java.util.List;

import static android.app.Notification.PRIORITY_MIN;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

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
		startForeground();				// To avoid shuttle service being killed.
		return START_NOT_STICKY;		// STICKY has no point here, since we share process with the direct service client (not the client in owner user).
	}

	@Override public void onTrimMemory(final int level) {
		if (level > TRIM_MEMORY_UI_HIDDEN) Log.i(TAG, "onTrimMemory: " + level);	// TRIM_MEMORY_UI_HIDDEN is always triggered since the shuttle is activity (UI).
	}

	private void startForeground() {
		startForeground(NotificationIds.SHUTTLE_KEEPER, mForegroundNotification.get());
	}

	@Override public void onCreate() {
		Log.d(TAG, "Start");
		mForegroundNotification = Suppliers.memoize(() ->
				new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setPriority(PRIORITY_MIN)
						.setContentTitle(SDK_INT >= N ? null : getString(R.string.app_name)).setSubText(getString(R.string.notification_standby_text)).build());
	}

	@Override public void onDestroy() {
		stopForeground(true);
		Log.d(TAG, "Stop");
	}

	private Supplier<Notification> mForegroundNotification;

	private static final BroadcastReceiver DUMMY_RECEIVER = new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent i) {}};
	private static final String TAG = "ShuttleKeeper";
}
