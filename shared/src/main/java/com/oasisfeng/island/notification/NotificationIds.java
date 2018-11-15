package com.oasisfeng.island.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationManagerCompat;

import com.oasisfeng.island.shared.R;

import java.util.Objects;

import java9.util.function.Consumer;

import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;

/**
 * Central definition for all notification IDs, to avoid conflicts.
 *
 * Created by Oasis on 2016/11/28.
 */
public enum NotificationIds {

	Provisioning(Channel.OngoingTask),
	UninstallHelper(Channel.Important),
	AppInstallation(Channel.Important),
	Debug(Channel.Debug, 999);

	public void post(final Context context, final Notification.Builder notification) {
		NotificationManagerCompat.from(context).notify(id(), buildChannel(context, notification).build());
	}

	public void post(final Context context, final String tag, final Notification.Builder notification) {
		NotificationManagerCompat.from(context).notify(tag, id(), buildChannel(context, notification).build());
	}

	public void cancel(final Context context) {
		NotificationManagerCompat.from(context).cancel(id());
	}

	public void startForeground(final Service service, final Notification.Builder notification) {
		service.startForeground(id(), buildChannel(service, notification).build());
	}

	private Notification.Builder buildChannel(final Context context, final Notification.Builder notification) {
		if (SDK_INT < O) return notification;
		return notification.setChannelId(channel.createAndGetId(context));
	}

	private int id() { return id != 0 ? id : ordinal() + 1; }		// 0 is reserved

	NotificationIds(final Channel channel) { this(channel, 0); }
	NotificationIds(final Channel channel, final int id) { this.channel = channel; this.id = id; }

	private final Channel channel;
	private final int id;

	@SuppressLint("InlinedApi") enum Channel {

		OngoingTask	("OngoingTask",	R.string.notification_channel_ongoing_task,		IMPORTANCE_HIGH, channel -> channel.setShowBadge(false)),
		Important	("Important",	R.string.notification_channel_important,		IMPORTANCE_HIGH, channel -> channel.setShowBadge(true)),
		Debug		("Debug",		R.string.notification_channel_debug,			IMPORTANCE_MIN,  channel -> channel.setShowBadge(false));

		Channel(final String name, final @StringRes int title, final int importance, final @Nullable Consumer<NotificationChannel> tweaks) {
			this.name = name; this.title = title; this.importance = importance; this.tweaks = tweaks;
		}

		@RequiresApi(O) String createAndGetId(final Context context) {
			if (! created) {
				final NotificationChannel channel = new NotificationChannel(name, context.getString(title), importance);
				if (tweaks != null) tweaks.accept(channel);
				Objects.requireNonNull(context.getSystemService(NotificationManager.class)).createNotificationChannel(channel);
				created = true;
			}
			return name;
		}

		private final String name;
		private final @StringRes int title;
		private final int importance;
		private final @Nullable Consumer<NotificationChannel> tweaks;
		private boolean created;
	}
}
