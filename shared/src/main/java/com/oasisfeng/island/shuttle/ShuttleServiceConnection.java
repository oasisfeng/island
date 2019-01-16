package com.oasisfeng.island.shuttle;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Special {@link ServiceConnection} to be used together with ServiceShuttle. Cannot be shared by more than one service.
 *
 * Created by Oasis on 2016/10/20.
 */
public abstract class ShuttleServiceConnection implements ServiceConnection {

	IBinder createDispatcher() {
		if (mDispatcher != null) {
			Log.w(TAG, "Leaked: " + mDispatcher);
			mDispatcher.close();
		}
		return mDispatcher = new Dispatcher(this);
	}

	boolean unbind() {
		final Dispatcher dispatcher = mDispatcher;
		if (dispatcher == null) throw new IllegalStateException("Already unbound");
		mDispatcher = null;
		return dispatcher.close();
	}

	void callServiceConnected() {
		if (mDispatcher != null && mDispatcher.mConnection != null && mDispatcher.mConnectedService != null && mDispatcher.mConnectedService.binder != null)
			mDispatcher.mConnection.onServiceConnected(mDispatcher.mConnectedService.binder);
	}

	private static class Dispatcher extends IServiceConnection.Stub implements IBinder.DeathRecipient {

		boolean close() {
			mConnection = null;
			if (mConnectedService == null) return true;

			mConnectedService.binder.unlinkToDeath(this, 0);
			Log.v(TAG, "Stop monitoring: " + this);

			try {
				return mConnectedService.unbinder.unbind();
			} catch (final RemoteException ignored) { return false; }
		}

		@Override public void onServiceConnected(final ComponentName name, final IBinder service, final IUnbinder unbinder) {
			if (mConnection == null) return;	// Already closed
			Log.d(TAG, "Service connected: " + name.flattenToShortString());
			mConnectedService = new ConnectedService(name, service, unbinder);

			try {
				Log.v(TAG, "Start monitoring: " + this);
				service.linkToDeath(this, 0);
			} catch (final RemoteException e) {
				Log.e(TAG, "Service already died", e);
				return;
			}

			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(() -> { if (mConnection != null) mConnection.onServiceConnected(service); });
			else mConnection.onServiceConnected(service);
		}

		@Override public void onServiceDisconnected(final ComponentName name) {
			if (mConnection == null) return;	// Already closed
			Log.d(TAG, "Service disconnected: " + name.flattenToShortString());
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(mConnection::onServiceDisconnected);
			else mConnection.onServiceDisconnected();
		}

		@Override public void onServiceFailed() {
			if (mConnection == null) return;	// Already closed
			if (Thread.currentThread() != Looper.getMainLooper().getThread())
				new Handler(Looper.getMainLooper()).post(mConnection::onServiceFailed);
			else mConnection.onServiceFailed();
		}

		@Override public void binderDied() {
			if (mConnection == null) return;	// Already closed
			Log.w(TAG, "Service disconnected (process died): " + mConnectedService.getComponentShortName());
			mConnection.onServiceDisconnected();
		}

		@Override public String toString() {
			if (mConnectedService == null) return super.toString();
			return new StringBuilder("ShuttleServiceConnection{").append(System.identityHashCode(this)).append(", comp=")
					.append(mConnectedService.component.flattenToShortString()).append(", binder=").append(mConnectedService.binder).toString();
		}

		Dispatcher(final @NonNull ShuttleServiceConnection connection) { this.mConnection = connection; }

		private @Nullable ShuttleServiceConnection mConnection;
		private ConnectedService mConnectedService;

		static class ConnectedService {

			ConnectedService(final ComponentName component, final IBinder binder, final IUnbinder unbinder) {
				this.binder = binder; this.component = component; this.unbinder = unbinder;
			}

			String getComponentShortName() { return component != null ? component.flattenToShortString() : null; }

			final IBinder binder;
			final ComponentName component;
			final IUnbinder unbinder;
		}
	}

	public abstract void onServiceConnected(final IBinder service);
	public abstract void onServiceDisconnected();
	public abstract void onServiceFailed();

	// Pass-through for non-shuttled service
	@Override public final void onServiceConnected(final ComponentName name, final IBinder service) { onServiceConnected(service); }
	@Override public final void onServiceDisconnected(final ComponentName name) { onServiceDisconnected(); }

	private Dispatcher mDispatcher;

	private static final String TAG = "ShuttleSvcConn";
}
