package com.oasisfeng.island.shuttle;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.BuildConfig;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.ProfileUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_NO_HISTORY;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * Bind to service via activity
 *
 * Created by Oasis on 2016/8/21.
 */
public class ServiceShuttle extends Activity {

	private static final long SHUTTLED_SERVICE_DISCONNECTION_DELAY = BuildConfig.DEBUG ? 5_000 : 30_000;	// Delay before actual disconnection from shuttled service
	private static final boolean ALWAYS_USE_SHUTTLE = false;		// For test purpose

	public static final String ACTION_BIND_SERVICE = "com.oasisfeng.island.action.BIND_SERVICE";
	private static final String EXTRA_INTENT = "extra";
	private static final String EXTRA_SERVICE_CONNECTION = "svc_conn";
	private static final String EXTRA_FLAGS = "flags";

	/**
	 * Please delegate the regular {@link Context#bindService(Intent, ServiceConnection, int)} of your components and application to this method.
	 *
	 * <pre>@Override public boolean bindService(Intent service, ServiceConnection conn, int flags) {
	 *     return ServiceShuttle.bindService(this, service, conn, flags) || super.bindService(service, conn, flags);
	 * }</pre>
	 */
	public static boolean bindService(final Context context, final Intent service, final ServiceConnection conn, final int flags) {
		if (GlobalStatus.profile == null || ! (conn instanceof ShuttleServiceConnection)) return false;
		if (! ALWAYS_USE_SHUTTLE && ActivityCompat.checkSelfPermission(context, Hacks.Permission.INTERACT_ACROSS_USERS) == PERMISSION_GRANTED)
			if (Hacks.Context_bindServiceAsUser.invoke(service, conn, flags, GlobalStatus.profile).on(context)) {
				Log.d(TAG, "Connecting to service in profile: " + service);
				return true;
			}
		return bindServiceViaShuttle(context, service, (ShuttleServiceConnection) conn, flags);
	}

	private static final int SHUTTLE_ACTIVITY_START_FLAGS = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK
			| FLAG_ACTIVITY_NO_USER_ACTION | FLAG_ACTIVITY_NO_ANIMATION | FLAG_ACTIVITY_NO_HISTORY;

	private static boolean bindServiceViaShuttle(final Context context, final Intent service, final ShuttleServiceConnection conn, final int flags) {
		if (sPendingUnbind.remove(conn)) {		// Reuse the still connected service, which is pending disconnection.
			Log.d(TAG, "Reuse service: " + conn);
			sMainHandler.post(conn::callServiceConnected);
			return true;
		}

		@SuppressWarnings("deprecation") final ResolveInfo resolve = context.getPackageManager().resolveService(service, PackageManager.GET_DISABLED_COMPONENTS);
		if (resolve == null) return false;		// Unresolvable even in disabled services
		final Bundle extras = new Bundle(); extras.putBinder(EXTRA_SERVICE_CONNECTION, conn.createDispatcher());
		try {
			final Activity activity = Activities.findActivityFrom(context);
			if (activity != null) activity.overridePendingTransition(0, 0);

			Activities.startActivity(context, new Intent(ACTION_BIND_SERVICE).addFlags(SHUTTLE_ACTIVITY_START_FLAGS).putExtras(extras)
					.putExtra(EXTRA_INTENT, service).putExtra(EXTRA_FLAGS, flags));
			Log.d(TAG, "Connecting to service in profile (via shuttle): " + service);
			return true;
		} catch (final ActivityNotFoundException e) {
			return false;		// ServiceShuttle not ready in managed profile
		}
	}

	/** Beware: You should pass the <b>base</b> context instead of current context itself as the first parameter,
	 *  otherwise {@link Context#unbindService(ServiceConnection)} will be called here, causing {@link StackOverflowError} */
	public static boolean unbindService(final Context context, final ServiceConnection connection) {
		if (GlobalStatus.profile == null || ! (connection instanceof ShuttleServiceConnection)) return false;
		if (ALWAYS_USE_SHUTTLE || ActivityCompat.checkSelfPermission(context, Hacks.Permission.INTERACT_ACROSS_USERS) != PERMISSION_GRANTED)
			return unbindShuttledServiceDelayed((ShuttleServiceConnection) connection);
		try {
			context.unbindService(connection);
			return true;
		} catch (final IllegalArgumentException e) { return false; }		// IllegalArgumentException: Service not registered
	}

	private static boolean unbindShuttledServiceDelayed(final ShuttleServiceConnection conn) {
		Log.v(TAG, "Schedule service unbinding: " + conn);
		sPendingUnbind.add(conn);
		sMainHandler.removeCallbacks(sDelayedUnbindAll);
		sMainHandler.postDelayed(sDelayedUnbindAll, SHUTTLED_SERVICE_DISCONNECTION_DELAY);
		return true;
	}

	private static void unbindPendingShuttledServices() {
		synchronized (sPendingUnbind) {
			for (final ShuttleServiceConnection conn : sPendingUnbind) try {
				Log.d(TAG, "Unbind service: " + conn);
				final boolean result = conn.unbind();
				if (! result) Log.w(TAG, "Remote service died before unbinding: " + conn);
			} catch (final RuntimeException e) {
				Log.e(TAG, "Error unbinding" + conn, e);
			}
			sPendingUnbind.clear();
		}
	}

	private static final Runnable sDelayedUnbindAll = ServiceShuttle::unbindPendingShuttledServices;
	private static final Set<ShuttleServiceConnection> sPendingUnbind = Collections.synchronizedSet(new HashSet<>());

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		handleIntent(getIntent());
		finish();
	}

	@Override protected void onNewIntent(final Intent intent) {
		handleIntent(intent);
		finish();
	}

	@ProfileUser private void handleIntent(final Intent intent) {
		setResult(RESULT_CANCELED);
		final IBinder remote_connection = intent.getExtras().getBinder(EXTRA_SERVICE_CONNECTION);
		if (remote_connection == null) return;

		if (ACTION_BIND_SERVICE.equals(intent.getAction())) {
			final Intent service_intent = intent.getParcelableExtra(EXTRA_INTENT);
			if (service_intent == null) return;
			final ResolveInfo resolve = getPackageManager().resolveService(service_intent, 0);
			if (resolve == null) return;
			final ServiceInfo service = resolve.serviceInfo;
			service_intent.setComponent(new ComponentName(service.packageName, service.name));
			final Context binding_context = getApplicationContext();	// Application context for longer lifespan
			try {
				final DelegateServiceConnection connection = new DelegateServiceConnection(binding_context, service_intent, remote_connection);
				Log.d(TAG, "Bind " + remote_connection + " to " + intent);
				@SuppressWarnings("WrongConstant") final boolean result = binding_context.bindService(service_intent, connection, intent.getIntExtra(EXTRA_FLAGS, 0));
				if (result) setResult(RESULT_OK);
			} catch (final RemoteException ignored) {}
		}
	}

	private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
	private static final String TAG = "Shuttle";

	/** Delegate ServiceConnection running in target user, delivering callbacks back to the caller in originating user. */
	private static class DelegateServiceConnection extends IUnbinder.Stub implements ServiceConnection, IBinder.DeathRecipient {

		DelegateServiceConnection(final Context context, final Intent intent, final IBinder delegate) throws RemoteException {
			this.context = context;
			this.intent = intent.cloneFilter();
			this.delegate = IServiceConnection.Stub.asInterface(delegate);
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
			try {
				delegate.asBinder().linkToDeath(this, 0);
			} catch (final RemoteException e) {		// Already died
				doUnbind();
				return;
			}
			try { delegate.onServiceConnected(name, service, this); }
			catch (final RemoteException ignored) { return; }
			ShuttleKeeper.onServiceConnected(context);
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			Log.w(TAG, "Service disconnected: " + intent);
			delegate.asBinder().unlinkToDeath(this, 0);
			try { delegate.onServiceDisconnected(name); }
			catch (final RemoteException ignored) {}
		}

		@Override public boolean unbind() throws RemoteException {
			Log.d(TAG, "Unbind " + delegate.asBinder() + " from " + intent);
			delegate.asBinder().unlinkToDeath(this, 0);
			return doUnbind();
		}

		@Override public void binderDied() {
			Log.w(TAG, "Service client died, unbind " + delegate.asBinder() + " from " + intent);
			delegate.asBinder().unlinkToDeath(this, 0);
			doUnbind();
		}

		private boolean doUnbind() {
			try {
				context.unbindService(this);
			} catch (final RuntimeException e) {		// IllegalArgumentException: Service not registered
				Log.e(TAG, "Failed to unbind service", e);
				return false;
			}
			ShuttleKeeper.onServiceUnbind(context, intent);
			return true;
		}

		private final Context context;
		private final Intent intent;
		private final IServiceConnection delegate;
	}
}
