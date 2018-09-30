package com.oasisfeng.island.shuttle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.oasisfeng.island.util.ProfileUser;

/**
 * Proxy activity to implement {@link ServiceShuttle}.
 *
 * Created by Oasis on 2016/8/21.
 */
public abstract class ServiceShuttleActivity extends Activity {

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
		final Bundle extras = intent.getExtras();
		if (extras == null) return;
		final IServiceConnection remote_connection = IServiceConnection.Stub.asInterface(extras.getBinder(ServiceShuttle.EXTRA_SERVICE_CONNECTION));
		if (remote_connection == null) return;

		if (ServiceShuttle.ACTION_BIND_SERVICE.equals(intent.getAction())) {
			final Intent service_intent = intent.getParcelableExtra(ServiceShuttle.EXTRA_INTENT);
			if (service_intent == null) return;
			final Context binding_context = getApplicationContext();	// Application context for longer lifespan
			final DelegateServiceConnection connection = new DelegateServiceConnection(binding_context, service_intent, remote_connection);
			Log.d(TAG, "Bind " + remote_connection + " to " + intent);
			final int flags = intent.getIntExtra(ServiceShuttle.EXTRA_FLAGS, 0);
			@SuppressWarnings("WrongConstant") final boolean result = binding_context.bindService(service_intent, connection, flags);
			if (! result) try { remote_connection.onServiceFailed(); } catch (final RemoteException ignored) {}
		}
	}

	private static final String TAG = "Island.SSA";

	/** Delegate ServiceConnection running in target user, delivering callbacks back to the caller in originating user. */
	private static class DelegateServiceConnection extends IUnbinder.Stub implements ServiceConnection, IBinder.DeathRecipient {

		DelegateServiceConnection(final Context context, final Intent intent, final IServiceConnection delegate) {
			this.context = context;
			this.intent = intent.cloneFilter();
			this.delegate = delegate;
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
			try {
				delegate.asBinder().linkToDeath(this, 0);
			} catch (final RemoteException e) {		// Already died
				doUnbind();
				return;
			}
			try { delegate.onServiceConnected(name, service, this); }
			catch (final RemoteException ignored) {}
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			Log.w(TAG, "Service disconnected: " + intent);
			delegate.asBinder().unlinkToDeath(this, 0);
			try { delegate.onServiceDisconnected(name); }
			catch (final RemoteException ignored) {}
		}

		@Override public boolean unbind() {
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
			return true;
		}

		private final Context context;
		private final Intent intent;
		private final IServiceConnection delegate;
	}
}
