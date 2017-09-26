package com.oasisfeng.island.shuttle;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.oasisfeng.island.util.ProfileUser;

import java.util.List;

/**
 * Proxy activity to implement {@link ServiceShuttle}.
 *
 * Created by Oasis on 2016/8/21.
 */
public class ServiceShuttleActivity extends Activity {

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
		final IBinder remote_connection = intent.getExtras().getBinder(ServiceShuttle.EXTRA_SERVICE_CONNECTION);
		if (remote_connection == null) return;

		if (ServiceShuttle.ACTION_BIND_SERVICE.equals(intent.getAction())) {
			final Intent service_intent = intent.getParcelableExtra(ServiceShuttle.EXTRA_INTENT);
			if (service_intent == null) return;
			final ResolveInfo resolve = getPackageManager().resolveService(service_intent, 0);
			if (resolve == null) return;
			final ServiceInfo service = resolve.serviceInfo;
			service_intent.setComponent(new ComponentName(service.packageName, service.name));
			final Context binding_context = getApplicationContext();	// Application context for longer lifespan
			try {
				final DelegateServiceConnection connection = new DelegateServiceConnection(binding_context, service_intent, remote_connection);
				Log.d(TAG, "Bind " + remote_connection + " to " + intent);
				@SuppressWarnings("WrongConstant") final boolean result = binding_context.bindService(service_intent, connection, intent.getIntExtra(ServiceShuttle.EXTRA_FLAGS, 0));
				if (result) setResult(RESULT_OK);
			} catch (final RemoteException ignored) {}
		}
	}

	private static final String TAG = ServiceShuttleActivity.class.getSimpleName();

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
			context.startService(new Intent(context, ShuttleKeeper.class));
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
			stopShuttleKeeperIfNeeded(context, intent);
			return true;
		}

		private static void stopShuttleKeeperIfNeeded(final Context context, final Intent intent) {
			if (DUMMY_RECEIVER.peekService(context, intent) != null) return;	// Fast check for common cases

			final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			final List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
			String pkg = intent.getPackage();
			if (pkg == null) {
				final ResolveInfo resolved = context.getPackageManager().resolveService(intent, 0);
				if (resolved == null) throw new IllegalArgumentException("Service not found: " + intent);
				pkg = resolved.serviceInfo.packageName;
			}
			for (final ActivityManager.RunningServiceInfo service : services) {
				if (! pkg.equals(service.service.getPackageName())) continue;
				if (service.clientCount != 0) return;		// Service is still bound
			}
			context.stopService(new Intent(context, ShuttleKeeper.class));
		}

		private final Context context;
		private final Intent intent;
		private final IServiceConnection delegate;

		private static final BroadcastReceiver DUMMY_RECEIVER = new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent i) {}};
	}
}
