package android.app;

import android.content.Context;
import android.os.Handler;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.List;

import static android.os.Build.VERSION_CODES.O;

/**
 * Helper to extend {@link NotificationManager}
 *
 * Created by Oasis on 2018/1/6.
 */
public class NotificationManagerExtender extends NotificationManager {

	@Override public void notify(final int id, final Notification notification) {
		delegate().notify(id, notification);
	}

	@Override public void notify(final String tag, final int id, final Notification notification) {
		delegate().notify(tag, id, notification);
	}

	@Override public void cancel(final int id) {
		delegate().cancel(id);
	}

	@Override public void cancel(final String tag, final int id) {
		delegate().cancel(tag, id);
	}

	@Override public void cancelAll() {
		delegate().cancelAll();
	}

	@Override public StatusBarNotification[] getActiveNotifications() {
		return delegate().getActiveNotifications();
	}

	@Override public int getImportance() {
		return delegate().getImportance();
	}

	@Override public boolean areNotificationsEnabled() {
		return delegate().areNotificationsEnabled();
	}

	@RequiresApi(O) @Override public NotificationChannel getNotificationChannel(final String channelId) {
		return delegate().getNotificationChannel(channelId);
	}

	@RequiresApi(O) @Override public List<NotificationChannel> getNotificationChannels() {
		return delegate().getNotificationChannels();
	}

	@RequiresApi(O) @Override public List<NotificationChannelGroup> getNotificationChannelGroups() {
		return delegate().getNotificationChannelGroups();
	}

	@RequiresApi(O) @Override public void createNotificationChannel(@NonNull final NotificationChannel channel) {
		delegate().createNotificationChannel(channel);
	}

	@RequiresApi(O) @Override public void createNotificationChannelGroup(@NonNull final NotificationChannelGroup group) {
		delegate().createNotificationChannelGroup(group);
	}

	@RequiresApi(O) @Override public void createNotificationChannelGroups(@NonNull final List<NotificationChannelGroup> groups) {
		delegate().createNotificationChannelGroups(groups);
	}

	@RequiresApi(O) @Override public void createNotificationChannels(@NonNull final List<NotificationChannel> channels) {
		delegate().createNotificationChannels(channels);
	}

	@RequiresApi(O) @Override public void deleteNotificationChannel(final String channelId) {
		delegate().deleteNotificationChannel(channelId);
	}

	@RequiresApi(O) @Override public void deleteNotificationChannelGroup(final String groupId) {
		delegate().deleteNotificationChannelGroup(groupId);
	}

	public NotificationManagerExtender(final Context context) {
		mDelegate = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	private NotificationManager delegate() { return mDelegate; }

	private final NotificationManager mDelegate;
}
