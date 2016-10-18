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
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.util.Hacks;

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

	public static final String ACTION_BIND_SERVICE = "com.oasisfeng.island.action.BIND_SERVICE";
	private static final String EXTRA_INTENT = "extra";
	private static final String EXTRA_SERVICE_CONNECTION = "svc_conn";
	private static final String EXTRA_FLAGS = "flags";

	private static final boolean ALWAYS_USE_SHUTTLE = true;

	public static abstract class ShuttleServiceConnection extends IServiceConnection.Stub implements ServiceConnection {

		public abstract void onServiceConnected(final IBinder service);
		public abstract void onServiceDisconnected();

		@Override public final void onServiceConnected(final ComponentName name, final IBinder service) { onServiceConnected(service); }

		@Override public final void onServiceConnected(final ComponentName name, final IBinder service, final IBinder unbinder) {
			if (this.unbinder == this) {	// Unbind() is requested before
				unbinder.pingBinder();
				return;
			}
			this.unbinder = unbinder;
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(() -> onServiceConnected(service));
			else onServiceConnected(service);
		}

		@Override public final void onServiceDisconnected(final ComponentName name) {
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(this::onServiceDisconnected);
			else onServiceDisconnected();
		}

		boolean unbind() {
			if (unbinder != null) return unbinder.pingBinder();
			unbinder = this;	// Special mark to indicate pending unbinding.
			return true;
		}

		private IBinder unbinder;
	}

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
		@SuppressWarnings("deprecation") final ResolveInfo resolve = context.getPackageManager().resolveService(service, PackageManager.GET_DISABLED_COMPONENTS);
		if (resolve == null) return false;		// Unresolvable even in disabled services
		final Bundle extras = new Bundle(); extras.putBinder(EXTRA_SERVICE_CONNECTION, conn);
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

	public static boolean unbindService(final Context context, final ServiceConnection connection) {
		if (GlobalStatus.profile == null || ! (connection instanceof ShuttleServiceConnection)) return false;
		if (ALWAYS_USE_SHUTTLE || ActivityCompat.checkSelfPermission(context, Hacks.Permission.INTERACT_ACROSS_USERS) != PERMISSION_GRANTED)
			return unbindService((ShuttleServiceConnection) connection);
		try {
			context.unbindService(connection);
			return true;
		} catch (final IllegalArgumentException e) { return false; }		// IllegalArgumentException: Service not registered
	}

	// TODO: Use service binder to unbind service, to get rid of the "work profile" toast and avoid breaking recent-task panel.
	private static boolean unbindService(final ShuttleServiceConnection conn) {
		final boolean result = conn.unbind();
		if (! result) Log.w(TAG, "Remote service died before unbinding: " + conn);
		return result;
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		handleIntent(getIntent());
		finish();
	}

	@Override protected void onNewIntent(final Intent intent) {
		handleIntent(intent);
		finish();
	}

	private void handleIntent(final Intent intent) {
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
			try {
				final DelegateServiceConnection delegate_connection = new DelegateServiceConnection(this, remote_connection);
				@SuppressWarnings("WrongConstant") final boolean result = getApplicationContext()/* Application context for longer lifespan */
						.bindService(service_intent, delegate_connection, intent.getIntExtra(EXTRA_FLAGS, 0));
				if (result) setResult(RESULT_OK);
			} catch (final RemoteException ignored) {}
		}
	}

	private static final String TAG = "Shuttle";

	/** Delegate ServiceConnection running in target user, delivering callbacks back to the caller in originating user. */
	static class DelegateServiceConnection extends Binder implements ServiceConnection, IBinder.DeathRecipient {

		DelegateServiceConnection(final Context context, final IBinder delegate) throws RemoteException {
			this.context = context.getApplicationContext();
			this.delegate = IServiceConnection.Stub.asInterface(delegate);
			delegate.linkToDeath(this, 0);
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
			try { delegate.onServiceConnected(name, service, this); } catch (final RemoteException ignored) {}
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			try { delegate.onServiceDisconnected(name); } catch (final RemoteException ignored) {}
		}

		/** Serve as an API for unbinding */
		@Override public boolean pingBinder() {
			try {
				context.unbindService(this);
				return true;
			} catch (final RuntimeException e) {		// IllegalArgumentException: Service not registered
				Log.e(TAG, "Failed to unbind service", e);
				return false;
			}
		}

		@Override public void binderDied() {
			Log.w(TAG, "Remote service client died.");
			try { context.unbindService(this); } catch (final RuntimeException ignored) {}
		}

		private final Context context;
		private final IServiceConnection delegate;
	}
}
