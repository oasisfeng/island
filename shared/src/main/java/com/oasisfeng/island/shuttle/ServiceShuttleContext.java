package com.oasisfeng.island.shuttle;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Context wrapper for service shuttle
 *
 * Created by Oasis on 2017/2/16.
 */
public class ServiceShuttleContext extends ContextWrapper {

	public static final boolean ALWAYS_USE_SHUTTLE = Boolean.FALSE;		// For test purpose

	public ServiceShuttleContext(final Context base) { super(base); }

	@Override public boolean bindService(final Intent service, final ServiceConnection connection, final int flags) {
		final UserHandle profile = Users.profile;
		if (profile == null) return false;
		if (! ALWAYS_USE_SHUTTLE && Permissions.ensure(this, Permissions.INTERACT_ACROSS_USERS)) try {
			if (Hacks.Context_bindServiceAsUser.invoke(service, connection, flags, profile).on(getBaseContext())) {
				Log.d(TAG, "Connecting to service in profile: " + service);
				return true;
			}
		} catch (final SecurityException ignored) {}	// Very few devices still throw SecurityException, cause known.

		final ShuttleServiceConnection existent = mConnections.get(connection);
		final ShuttleServiceConnection shuttle_connection = existent != null ? existent : connection instanceof ShuttleServiceConnection
				? (ShuttleServiceConnection) connection : new ShuttleServiceConnection() {
					@Override public void onServiceConnected(final IBinder service) {
						connection.onServiceConnected(null, service);
					}
					@Override public void onServiceDisconnected() {
						connection.onServiceDisconnected(null);
					}
					@Override public void onServiceFailed() { Log.w(TAG, "Service failed: " + service); }
					@Override public String toString() { return TAG + "{" + connection + "}"; }
				};

		final boolean result = ServiceShuttle.bindServiceViaShuttle(getBaseContext(), service, shuttle_connection, flags);
		if (result && existent == null) mConnections.put(connection, shuttle_connection);
		return result;
	}

	@Override public void unbindService(final ServiceConnection connection) {
		if (Users.profile == null) return;
		if (! ALWAYS_USE_SHUTTLE && Permissions.has(this, Permissions.INTERACT_ACROSS_USERS)) {
			getBaseContext().unbindService(connection);
			return;
		}
		final ShuttleServiceConnection shuttle_connection = mConnections.get(connection);
		if (shuttle_connection == null) Log.e(TAG, "Service not registered: " + connection);
		else ServiceShuttle.unbindShuttledServiceDelayed(shuttle_connection);
	}

	private final Map<ServiceConnection, ShuttleServiceConnection> mConnections = Collections.synchronizedMap(new WeakHashMap<>());

	private static final String TAG = "ShuttleContext";
}
