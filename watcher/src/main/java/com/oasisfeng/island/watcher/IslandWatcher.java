package com.oasisfeng.island.watcher;

import android.app.Activity;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.util.ProfileUser;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static android.app.Notification.CATEGORY_PROGRESS;
import static android.app.Notification.CATEGORY_STATUS;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;

/**
 * Watch recently started managed-profile and offer action to stop.
 *
 * Created by Oasis on 2019-2-25.
 */
@ProfileUser public class IslandWatcher extends BroadcastReceiver {

	// With shared notification group, app watcher (group child) actually hides Island watcher (group summary), which only shows up if no app watchers.
	static final String GROUP = "Watcher";

	@Override public void onReceive(final Context context, final Intent intent) {
		Log.d(TAG, "onReceive: " + intent);
		// ACTION_LOCKED_BOOT_COMPLETED is unnecessary, because requestQuietModeEnabled() does not work if user is still locked.
		if (! Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && ! Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
		if (! ((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE)).isProfileOwnerApp(context.getPackageName())) {
			context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, getClass()), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
			return;		// We don't need this receiver in owner user.
		}
		if (SDK_INT < O) return;
		if (NotificationIds.IslandWatcher.isBlocked(context)) return;

		final PendingIntent deactivate = PendingIntent.getService(context, 0, new Intent(context, IslandDeactivationService.class), FLAG_UPDATE_CURRENT),
				settings = PendingIntent.getActivity(context, 0, NotificationIds.IslandWatcher.buildChannelSettingsIntent(context), FLAG_UPDATE_CURRENT);
		NotificationIds.IslandWatcher.post(context, new Notification.Builder(context, null).setOngoing(true).setGroup(GROUP).setGroupSummary(true)
				.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(context.getColor(R.color.primary)).setCategory(CATEGORY_STATUS)
				.setLargeIcon(Icon.createWithBitmap(getAppIcon(context))).setVisibility(Notification.VISIBILITY_PUBLIC)
				.setContentTitle(context.getText(R.string.notification_island_watcher_title))
				.setContentText(context.getText(R.string.notification_island_watcher_text))
				.addAction(new Action.Builder(null, context.getText(R.string.action_deactivate_island), deactivate).build())
				.addAction(new Action.Builder(null, context.getText(R.string.action_settings), settings).build()));
	}

	private static Bitmap getAppIcon(final Context context) {
		final Drawable icon = context.getApplicationInfo().loadIcon(context.getPackageManager());
		final int size = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
		final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		icon.setBounds(0, 0, size, size);
		icon.draw(canvas);
		return bitmap;
	}

	@RequiresApi(P) public static class IslandDeactivationService extends Service {

		@Override public int onStartCommand(final Intent intent, final int flags, final int startId) {
			final UserManager um = (UserManager) getSystemService(USER_SERVICE);
			final UserHandle this_user = Process.myUserHandle();
			// requestQuietModeEnabled() requires us running as foreground (service).
			NotificationIds.IslandWatcher.startForeground(this, new Notification.Builder(this, null)
					.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(getColor(R.color.primary)).setCategory(CATEGORY_PROGRESS)
					.setProgress(0, 0, true).setContentTitle("Deactivating Island space..."));
			try {
				um.requestQuietModeEnabled(true, this_user);
			} catch (final SecurityException ignored) {		// Fall-back to manual control
				try {
					startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					Toast.makeText(getApplicationContext(), R.string.toast_manual_quiet_mode, Toast.LENGTH_LONG).show();
				} catch (final ActivityNotFoundException ex) {
					Toast.makeText(getApplicationContext(), "Sorry, ROM is incompatible.", Toast.LENGTH_LONG).show();
				}
			} finally {
				stopForeground(true);
			}
			return START_NOT_STICKY;
		}

		@Nullable @Override public IBinder onBind(final Intent intent) { return null; }
	}

	public static class DummyHomeActivity extends Activity {

		@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			startActivity(new Intent(ACTION_MAIN).addCategory(CATEGORY_HOME));
			finish();
		}
	}

	private static final String TAG = "Island.Watcher";
}
