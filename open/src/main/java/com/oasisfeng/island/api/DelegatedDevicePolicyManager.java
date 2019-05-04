package com.oasisfeng.island.api;

import android.app.admin.DerivedDevicePolicyManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import com.oasisfeng.island.RestrictedBinderProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static android.content.Context.DEVICE_POLICY_SERVICE;

/**
 * Delegated {@link DevicePolicyManager}
 *
 * Created by Oasis on 2019-4-30.
 */
public class DelegatedDevicePolicyManager extends DerivedDevicePolicyManager {

	/** Must only be called in isolated process */
	public DelegatedDevicePolicyManager(final Context context, final IBinder binder, final ComponentName admin) throws ReflectiveOperationException {
		super(context, (IInterface) Proxy.newProxyInstance(context.getClassLoader(), new Class[] { sHelper.getInterface() },
				new AdminComponentParameterAutoFiller(sHelper.asInterface(binder), admin)));
	}

	public DelegatedDevicePolicyManager(final Context c) throws ReflectiveOperationException {
		super(c, sHelper.asInterface(new RestrictedBinderProxy(sHelper.getService((DevicePolicyManager) c.getSystemService(DEVICE_POLICY_SERVICE)))));
	}

	public RestrictedBinderProxy getDelegatedBinderProxy() {
		return (RestrictedBinderProxy) sHelper.getService(this).asBinder();
	}

	static class AdminComponentParameterAutoFiller implements InvocationHandler {

		@Override public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			final Class<?>[] param_types = method.getParameterTypes();
			if (param_types.length > 0 && args != null && args.length > 0 && param_types[0] == ComponentName.class) {
				final ComponentName arg1 = (ComponentName) args[0];
				if (arg1 != null && arg1.getPackageName().isEmpty() && arg1.getClassName().isEmpty())	// Empty ComponentName as placeholder
					args[0] = mAdminComponent;
			}
			try {
				return method.invoke(mDevicePolicyManagerProxy, args);
			} catch (final InvocationTargetException e) {
				throw e.getTargetException();
			} catch (final IllegalAccessException e) {
				throw new SecurityException(e);
			}
		}

		AdminComponentParameterAutoFiller(final IInterface service, final ComponentName admin) {
			mDevicePolicyManagerProxy = service;
			mAdminComponent = admin;
		}

		private final ComponentName mAdminComponent;
		private final IInterface mDevicePolicyManagerProxy;
	}
}
