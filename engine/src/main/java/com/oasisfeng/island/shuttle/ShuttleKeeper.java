package com.oasisfeng.island.shuttle;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.notification.NotificationIds;

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

	private static final String TAG = "ShuttleKeeper";
}
