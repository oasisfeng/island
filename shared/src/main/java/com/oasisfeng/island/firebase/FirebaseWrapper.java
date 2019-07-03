package com.oasisfeng.island.firebase;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManagerExtender;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.oasisfeng.condom.CondomContext;
import com.oasisfeng.condom.CondomKit;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.island.IslandApplication;
import com.oasisfeng.island.util.Hacks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.annotation.ParametersAreNonnullByDefault;

import androidx.annotation.NonNull;

/**
 * Wrapper for Firebase with tweaks.
 *
 * Created by Oasis on 2018/1/6.
 */
@ParametersAreNonnullByDefault public class FirebaseWrapper {

	public static Context init() { return sFirebaseContext; }

	@SuppressLint("StaticFieldLeak") private static final Context sFirebaseContext;

	static {
		Context context = IslandApplication.$();
		if (! isGooglePlayServicesReady(context)) {
			// Block Google Play services if not ready (either missing or version too low), to force Firebase Analytics to use local implementation
			// and suppress the annoying notification of GMS missing or upgrade required.
			final CondomContext condom = CondomContext.wrap(context, "Firebase", new CondomOptions()
					.setOutboundJudge((t, i, pkg) -> ! "com.google.android.gms".equals(pkg)).addKit(new NotificationKit()).addKit(new PowerManagerKit()));
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

	private static class PowerManagerKit implements CondomKit {

		@Override public void onRegister(final CondomKitRegistry registry) {
			registry.registerSystemService(Context.POWER_SERVICE, (context, name) -> createWakeLockFreePowerManager(context));
		}

		private static PowerManager createWakeLockFreePowerManager(final Context context) {
			final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			final Object service = Hacks.PowerManager_mService.get(pm);
			if (! Proxy.isProxyClass(service.getClass()) || Proxy.getInvocationHandler(service).getClass() != PowerManagerServiceInvocationHandler.class)
				Hacks.PowerManager_mService.set(pm, Proxy.newProxyInstance(context.getClassLoader(), service.getClass().getInterfaces(),
						new PowerManagerServiceInvocationHandler(service)));
			return pm;
		}

		private static class PowerManagerServiceInvocationHandler implements InvocationHandler {

			@Override public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
				final String method_name = method.getName();
				if ("acquireWakeLock".equals(method_name) || "releaseWakeLock".equals(method_name)) return null;
				return method.invoke(mDelegate, args);
			}

			PowerManagerServiceInvocationHandler(final Object delegate) { mDelegate = delegate; }

			private final Object mDelegate;
		}
	}

	private static final String TAG = "FirebaseWrapper";
}
