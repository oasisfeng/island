package com.oasisfeng.island.shuttle;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Context wrapper for service shuttle
 *
 * Created by Oasis on 2017/2/16.
 */
public class ShuttleContext extends ContextWrapper {

	public ShuttleContext(final Context base) { super(base); }

	@Override public boolean bindService(final Intent service, final ServiceConnection connection, final int flags) {
		final ShuttleServiceConnection existent = mConnections.get(connection);
		final ShuttleServiceConnection shuttle_connection = existent != null ? existent : new ShuttleServiceConnection() {
			@Override public void onServiceConnected(final IBinder service) {
				connection.onServiceConnected(null, service);
			}

			@Override public void onServiceDisconnected() {
				connection.onServiceDisconnected(null);
			}
		};
		if (ServiceShuttle.bindService(this, service, shuttle_connection, flags)) {
			if (existent == null) mConnections.put(connection, shuttle_connection);
			return true;
		} else return super.bindService(service, connection, flags);
	}

	@Override public void unbindService(final ServiceConnection connection) {
		final ShuttleServiceConnection shuttle_connection = mConnections.get(connection);
		if (shuttle_connection == null || ! ServiceShuttle.unbindService(getBaseContext(), shuttle_connection))
			super.unbindService(connection);
	}

	private final Map<ServiceConnection, ShuttleServiceConnection> mConnections = Collections.synchronizedMap(new WeakHashMap<>());
}
