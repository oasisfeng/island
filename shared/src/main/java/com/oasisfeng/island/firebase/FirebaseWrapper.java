package com.oasisfeng.island.firebase;

import android.app.Notification;
import android.app.NotificationManagerExtender;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.oasisfeng.condom.CondomContext;
import com.oasisfeng.condom.CondomKit;
import com.oasisfeng.condom.CondomOptions;

/**
 * Wrapper for Firebase with tweaks.
 *
 * Created by Oasis on 2018/1/6.
 */
public class FirebaseWrapper {

	public static void init(final Context context) {
		final CondomContext condom = CondomContext.wrap(context, "Firebase", new CondomOptions().addKit(new NotificationKit()));
		FirebaseApp.initializeApp(condom);
	}

	private static class NotificationKit implements CondomKit {

		@Override public void onRegister(final @NonNull CondomKitRegistry registry) {
			registry.registerSystemService(Context.NOTIFICATION_SERVICE, (context, name) -> new NotificationSuppressorManager(context));
		}

		static class NotificationSuppressorManager extends NotificationManagerExtender {

			@Override public void notify(final int id, final Notification notification) {
				Log.i(TAG, "Suppressed " + notification);
			}

			@Override public void notify(final String tag, final int id, final Notification notification) {
				Log.i(TAG, "Suppressed (" + tag + ") " + notification);
			}

			NotificationSuppressorManager(final Context context) { super(context); }

		}
	}

	static final String TAG = "FirebaseWrapper";
}
