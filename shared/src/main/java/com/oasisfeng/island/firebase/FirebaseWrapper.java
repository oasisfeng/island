package com.oasisfeng.island.firebase;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManagerExtender;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.oasisfeng.condom.CondomContext;
import com.oasisfeng.condom.CondomKit;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.island.IslandApplication;

/**
 * Wrapper for Firebase with tweaks.
 *
 * Created by Oasis on 2018/1/6.
 */
public class FirebaseWrapper {

	public static Context init() { return sFirebaseContext; }

	@SuppressLint("StaticFieldLeak") private static final Context sFirebaseContext;

	static {
		Context context = IslandApplication.$();
		if (! isGooglePlayServicesReady(context)) {
			// Block Google Play services if not ready (either missing or version too low), to force Firebase Analytics to use local implementation
			// and suppress the annoying notification of GMS missing or upgrade required.
			final CondomContext condom = CondomContext.wrap(context, "Firebase",
					new CondomOptions().setOutboundJudge((t, i, pkg) -> ! "com.google.android.gms".equals(pkg)).addKit(new NotificationKit()));
			FirebaseApp.initializeApp(condom);
			context = condom;
		} else FirebaseApp.initializeApp(context);
		sFirebaseContext = context;
	}

	private static boolean isGooglePlayServicesReady(final Context context) {
		if (context.getPackageManager().resolveContentProvider("com.google.android.gsf.gservices", 0) == null) return false;
		final int gms_availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
		return gms_availability == ConnectionResult.SUCCESS;
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
