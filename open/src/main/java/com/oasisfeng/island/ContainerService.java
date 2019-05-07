package com.oasisfeng.island;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.island.api.DelegatedAppOpsManager;
import com.oasisfeng.island.api.DelegatedDevicePolicyManager;
import com.oasisfeng.island.api.DelegatedPackageManager;
import com.oasisfeng.island.shuttle.MethodShuttle;
import com.oasisfeng.island.util.DeviceAdmins;
import com.oasisfeng.island.util.Users;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidx.annotation.Nullable;

import static android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;

/**
 * Isolated container service to run unsafe 3rd-party code.
 *
 * Created by Oasis on 2019-4-28.
 */
public class ContainerService extends Service {

	public static class Launcher extends Service {

		@Nullable @Override public IBinder onBind(final Intent intent) {
			final Intent selector = intent.getSelector();
			final ComponentName service_component = selector != null ? selector.getComponent() : null;
			if (service_component == null) {
				Log.w(TAG, "Target service component must be provided as selector intent via Intent.setSelector().");
				return null;
			}

			final UserHandle specified_user = intent.getParcelableExtra(Intent.EXTRA_USER);
			if (specified_user != null && ! Users.isProfile(specified_user)) {
				Log.w(TAG, specified_user + " is not a valid managed profile to bind service inside.");
				return null;
			}
			final UserHandle user = specified_user == null || Process.myUserHandle().equals(specified_user) ? null : specified_user;

			Log.d(TAG, "Requested binding to " + service_component.flattenToShortString() + (user != null ? " in " + user : ""));
			final LatchedBinderProxy latched_binder_proxy = new LatchedBinderProxy();
			if (user != null) {
				final ApplicationInfo app_info;
				try {
					app_info = getPackageManager().getApplicationInfo(service_component.getPackageName(), GET_UNINSTALLED_PACKAGES);
				} catch (final PackageManager.NameNotFoundException e) {
					Log.e(TAG, "Package of target service is not found: " + service_component.getPackageName());
					return null;
				}
				final Context context = this;
				MethodShuttle.runInProfile(this, () -> launch(context, service_component, latched_binder_proxy, app_info))
						.thenAccept(result -> { if (! result) latched_binder_proxy.cancel(null); })
						.exceptionally(t -> { latched_binder_proxy.cancel(t instanceof Exception ? (Exception) t : new Exception(t)); return null; });
				return latched_binder_proxy;
			} else {
				final boolean result = launch(this, service_component, latched_binder_proxy, null);
				if (result) return latched_binder_proxy;
				Log.e(TAG, "Failed to bind to container.");
			}
			return null;
		}

		private static boolean launch(final Context context, final ComponentName service, final LatchedBinderProxy latched_binder_proxy, final @Nullable ApplicationInfo app_info) {
			final Intent intent = new Intent(context, ContainerService.class).putExtra(EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(context));
			final ServiceConnection connection = new ServiceConnection() {

				@Override public void onServiceConnected(final ComponentName name, final IBinder binder) {
					if (context instanceof Launcher) ((Launcher) context).mServiceConnection = this;
					else context.unbindService(this);		// Just one-time binding for cross-profile launch, due to ServiceConnection incapable across profile.
					Log.i(TAG, "Preparing container");		// Delegated services must be created within the same user of requested service.
					RestrictedBinderProxy delegated_dpm_proxy = null;
					try {
						final DelegatedDevicePolicyManager delegated_dpm = new DelegatedDevicePolicyManager(context);
						delegated_dpm_proxy = delegated_dpm.getDelegatedBinderProxy();
						// Allowed API. TODO: Test
						delegated_dpm.setApplicationHidden(null, null, false);
						delegated_dpm.isApplicationHidden(null, null);
						delegated_dpm_proxy.seal();
					} catch (final ReflectiveOperationException e) {
						Log.e(TAG, "Error preparing delegated DevicePolicyManager", e);
					}

					RestrictedBinderProxy delegated_aom_proxy = null;
					try {
						final DelegatedAppOpsManager delegated_aom = new DelegatedAppOpsManager(context);
						delegated_aom_proxy = delegated_aom.getDelegatedBinderProxy();
						// Allowed API. TODO: Test
						AppOpsManager.class.getMethod("setMode", int.class, int.class, String.class, int.class).invoke(delegated_aom, 0, 0, null, 0);
						delegated_aom_proxy.seal();
					} catch (final ReflectiveOperationException e) {
						Log.e(TAG, "Error preparing delegated AppOpsManager", e);
					}

					final IBinder package_installer_binder = DelegatedPackageManager.getPackageInstallerBinder(context.getPackageManager().getPackageInstaller());

					final IContainer container = IContainer.Stub.asInterface(binder);
					try {
						if (delegated_dpm_proxy != null) container.registerSystemService(Context.DEVICE_POLICY_SERVICE, delegated_dpm_proxy);
						if (delegated_aom_proxy != null) container.registerSystemService(Context.APP_OPS_SERVICE, delegated_aom_proxy);
						container.registerSystemService(PackageInstaller.class.getName(), package_installer_binder);
					} catch (final RemoteException e) {
						latched_binder_proxy.cancel(new IllegalStateException("Error preparing container", e));
					}
					Log.i(TAG, "Binding to requested service: " + service.flattenToShortString());
					try {
						final IBinder external_service = container.loadService(service, app_info);
						if (external_service != null) latched_binder_proxy.setDelegate(external_service);
						else latched_binder_proxy.cancel(null);
					} catch (final Exception e) {
						latched_binder_proxy.cancel(e);
					}
				}

				@Override public void onServiceDisconnected(final ComponentName name) {}    // TODO: The external service crashed, how to handle?
			};
			return context.bindService(intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
		}

		@Override public void onDestroy() {
			if (mServiceConnection != null) unbindService(mServiceConnection);
			mServiceConnection = null;
		}

		private ServiceConnection mServiceConnection;
	}

	@Nullable @Override public IBinder onBind(final Intent intent) {
		mAdminComponent = intent.getParcelableExtra(EXTRA_DEVICE_ADMIN);
		return mBinder;
	}

	private final IContainer.Stub mBinder = new IContainer.Stub() {

		@Override public void registerSystemService(final String name, final IBinder service) {		// Runs inside the isolated process
			final IslandApplication app = (IslandApplication) getApplication();
			if (Context.DEVICE_POLICY_SERVICE.equals(name)) {
				try {
					app.registerSystemService(name, new DelegatedDevicePolicyManager(app, service, mAdminComponent));
				} catch (final ReflectiveOperationException e) {
					Log.e(TAG, "Error registering " + name, e);
				}
			} else if (Context.APP_OPS_SERVICE.equals(name)) {
				try {
					app.registerSystemService(name, new DelegatedAppOpsManager(app, service));
				} catch (final ReflectiveOperationException e) {
					Log.e(TAG, "Error registering " + name, e);
				}
			} else if (PackageInstaller.class.getName().equals(name)) try {
				app.registerPackageManager(new DelegatedPackageManager(app.getPackageManager(), service));
			} catch (final ReflectiveOperationException e) {
				Log.e(TAG, "Error registering " + name, e);
			}
		}

		@SuppressLint("PrivateApi") @Override public IBinder loadService(final ComponentName component, final ApplicationInfo app_info) throws RemoteException {
			final String flat_component = component.flattenToShortString();
			final Service service; final Method Service_attach;
			try {
				final Context service_context;
				if (app_info != null) { //noinspection JavaReflectionMemberAccess
					service_context = (Context) Context.class.getMethod("createApplicationContext", ApplicationInfo.class, int.class)
							.invoke(ContainerService.this, app_info, CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY);
				} else service_context = createPackageContext(component.getPackageName(), CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY);
				final Class<?> service_class = service_context.getClassLoader().loadClass(component.getClassName());
				service = (Service) service_class.newInstance();
				//noinspection JavaReflectionMemberAccess
				Service_attach = Service.class.getDeclaredMethod("attach",
						Context.class, Class.forName("android.app.ActivityThread"), String.class, IBinder.class, Application.class, Object.class);
				Service_attach.setAccessible(true);
			} catch (final PackageManager.NameNotFoundException | ClassCastException/* not Service */| ReflectiveOperationException e) {
				Log.e(TAG, "Error binding to service: " + flat_component, e);
				if (e instanceof ReflectiveOperationException) throw new RemoteException(e.toString());
				return null;
			}
			Log.d(TAG, "Attaching to service: " + flat_component);
			try { // ActivityThread is unused. Class name, token and IActivityManager are only for stopSelf() and startForeground(), not for bound service.
				Service_attach.invoke(service, getBaseContext(), null/* ActivityThread */, ""/* className */, null/* token */, getApplication(), null/* IActivityManager */);
				service.onCreate();
				Log.d(TAG, "Binding to service: " + flat_component);
				return service.onBind(null);
			} catch (final InvocationTargetException ite) {
				final Throwable e = ite.getTargetException();
				Log.e(TAG, "Error initializing service: " + flat_component, e);
				throw new RemoteException(e.toString());
			} catch (final IllegalAccessException e) {
				throw new RemoteException(e.toString());
			}
		}
	};

	private ComponentName mAdminComponent;

	private static final String TAG = "Island.Container";
}
