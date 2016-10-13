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
import android.util.SparseArray;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.util.Hacks;

import java.util.List;

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
	public static final String ACTION_UNBIND_SERVICE = "com.oasisfeng.island.action.UNBIND_SERVICE";
	private static final String EXTRA_INTENT = "extra";
	private static final String EXTRA_SERVICE_CONNECTION = "svc_conn";
	private static final String EXTRA_FLAGS = "flags";
	private static final String EXTRA_CONNECTION_HASH = "hash";

	private static final boolean ALWAYS_USE_SHUTTLE = true;

	public static abstract class ShuttleServiceConnection extends IServiceConnection.Stub implements ServiceConnection {

		public abstract void onServiceConnected(final IBinder service);
		public abstract void onServiceDisconnected();

		@Override public final void onServiceConnected(final ComponentName name, final IBinder service) {
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(() -> onServiceConnected(service));
			else onServiceConnected(service);
		}

		@Override public final void onServiceDisconnected(final ComponentName name) {
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(this::onServiceDisconnected);
			else onServiceDisconnected();
		}
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
					.putExtra(EXTRA_INTENT, service).putExtra(EXTRA_FLAGS, flags).putExtra(EXTRA_CONNECTION_HASH, System.identityHashCode(conn)));
			Log.d(TAG, "Connecting to service in profile (via shuttle): " + service);
			return true;
		} catch (final ActivityNotFoundException e) {
			return false;		// ServiceShuttle not ready in managed profile
		}
	}

	public static boolean unbindService(final Context context, final ServiceConnection connection) {
		if (GlobalStatus.profile == null || ! (connection instanceof ShuttleServiceConnection)) return false;
		if (! ALWAYS_USE_SHUTTLE && ActivityCompat.checkSelfPermission(context, Hacks.Permission.INTERACT_ACROSS_USERS) == PERMISSION_GRANTED) try {
			context.unbindService(connection);
			return true;
		} catch (final IllegalArgumentException e) { return false; }		// IllegalArgumentException: Service not registered
		return unbindService(context, (IServiceConnection.Stub) connection);
	}

	private static boolean unbindService(final Context context, final IServiceConnection.Stub conn) {
		try {
			Activities.startActivity(context, new Intent(ACTION_UNBIND_SERVICE).addFlags(SHUTTLE_ACTIVITY_START_FLAGS)
					.putExtra(EXTRA_CONNECTION_HASH, System.identityHashCode(conn)));
			return true;
		} catch (final ActivityNotFoundException e) {
			return false;		// ServiceShuttle not ready in managed profile
		}
	}

	private static final SparseArray</* hash -> */DelegateServiceConnection> mConnections = new SparseArray<>();

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final IServiceConnection service_connection = IServiceConnection.Stub.asInterface(intent.getExtras().getBinder(EXTRA_SERVICE_CONNECTION));

		setResult(RESULT_CANCELED);
		if (ACTION_BIND_SERVICE.equals(intent.getAction())) {
			final Intent service_intent = intent.getParcelableExtra(EXTRA_INTENT);
			if (service_intent != null && service_connection != null) {
				final List<ResolveInfo> matches = getPackageManager().queryIntentServices(service_intent, 0);
				for (final ResolveInfo match : matches) {
					Log.w("DEBUG", match.toString());
				}

				final ResolveInfo resolve = getPackageManager().resolveService(service_intent, 0);
				if (resolve != null) {
					final ServiceInfo service = resolve.serviceInfo;
					service_intent.setComponent(new ComponentName(service.packageName, service.name));
					final DelegateServiceConnection delegate_connection;
					try {
						delegate_connection = new DelegateServiceConnection(this, service_connection);
						mConnections.put(intent.getIntExtra(EXTRA_CONNECTION_HASH, 0), delegate_connection);
						@SuppressWarnings("WrongConstant") final boolean result = getApplicationContext()/* Application context for longer lifespan */
								.bindService(service_intent,delegate_connection, intent.getIntExtra(EXTRA_FLAGS, 0));
						if (result) setResult(RESULT_OK);
					} catch (final RemoteException ignored) {}
				}
			}
		} else if (ACTION_UNBIND_SERVICE.equals(intent.getAction())) {
			final int hash = intent.getIntExtra(EXTRA_CONNECTION_HASH, 0);
			if (hash == 0) return;
			final DelegateServiceConnection connection = mConnections.get(hash);
			if (connection == null) return;
			getApplicationContext().unbindService(connection);
		}
		finish();
	}

	private static final String TAG = "Shuttle";

	/** Delegate ServiceConnection running in target user, delivering callbacks back to the caller in originating user. */
	static class DelegateServiceConnection implements ServiceConnection, IBinder.DeathRecipient {

		DelegateServiceConnection(final Context context, final IServiceConnection delegate) throws RemoteException {
			this.context = context.getApplicationContext();
			this.delegate = delegate;
			delegate.asBinder().linkToDeath(this, 0);
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
			try { delegate.onServiceConnected(name, service); } catch (final RemoteException ignored) {}
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			try { delegate.onServiceDisconnected(name); } catch (final RemoteException ignored) {}
		}

		@Override public void binderDied() {
			context.unbindService(this);
		}

		private final Context context;
		private final IServiceConnection delegate;
	}
}
