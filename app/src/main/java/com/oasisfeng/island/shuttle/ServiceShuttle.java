package com.oasisfeng.island.shuttle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.oasisfeng.android.app.Activities;

/**
 * Bind to service via activity
 *
 * Created by Oasis on 2016/8/21.
 */
public class ServiceShuttle extends Activity {

	private static final String ACTION_BIND_SERVICE = "com.oasisfeng.island.action.BIND_SERVICE";
	private static final String EXTRA_INTENT = "extra";
	private static final String EXTRA_SERVICE_CONNECTION = "svc_conn";
	private static final String EXTRA_FLAGS = "flags";

	public static void bindService(final Context context, final Intent service, final IServiceConnection.Stub conn, final int flags) {
		final Bundle extras = new Bundle(); extras.putBinder(EXTRA_SERVICE_CONNECTION, conn);
		Activities.startActivity(context, new Intent(ACTION_BIND_SERVICE).putExtras(extras).putExtra(EXTRA_INTENT, service).putExtra(EXTRA_FLAGS, flags));
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final Intent service_intent = intent.getParcelableExtra(EXTRA_INTENT);
		final IServiceConnection service_connection = IServiceConnection.Stub.asInterface(intent.getExtras().getBinder(EXTRA_SERVICE_CONNECTION));
		setResult(RESULT_CANCELED);
		if (service_intent != null && service_connection != null) {
			final ResolveInfo resolve = getPackageManager().resolveService(service_intent, 0);
			if (resolve != null) {
				final ServiceInfo service = resolve.serviceInfo;
				service_intent.setComponent(new ComponentName(service.packageName, service.name));
				@SuppressWarnings("WrongConstant") final boolean result = bindService(service_intent,
						new DelegateServiceConnection(service_connection), intent.getIntExtra(EXTRA_FLAGS, 0));
				setResult(result ? RESULT_OK : RESULT_CANCELED);
			}
		}
		finish();
	}

	static class DelegateServiceConnection implements ServiceConnection {

		DelegateServiceConnection(final IServiceConnection delegate) {
			this.delegate = delegate;
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
			try { delegate.onServiceConnected(name, service); } catch (final RemoteException ignored) {}
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			try { delegate.onServiceDisconnected(name); } catch (final RemoteException ignored) {}
		}

		private final IServiceConnection delegate;
	}
}
