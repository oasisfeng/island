package com.oasisfeng.island.api;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import com.oasisfeng.island.RestrictedBinderProxy;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import java9.util.function.BiFunction;

/**
 * Created by Oasis on 2019-4-30.
 */
class DerivedManagerHelper<T> {

	RestrictedBinderProxy inject(final T manager, final Context context, final String service, final String delegation) throws ReflectiveOperationException {
		return inject(manager, context, service, (c, delegate) -> new RestrictedBinderProxy(c, delegation, delegate));
	}

	<P extends RestrictedBinderProxy> P inject(final T manager, final Context context, final String service,
											   final BiFunction<Context, IBinder, P> factory) throws ReflectiveOperationException {
		@SuppressWarnings("unchecked") final IBinder binder = getService((T) context.getSystemService(service)).asBinder();
		// The binder may already be injected due to system service cache (CachedServiceFetcher).
		final IBinder delegate = binder instanceof RestrictedBinderProxy ? ((RestrictedBinderProxy) binder).getDelegate() : binder;
		final P binder_proxy = factory.apply(context, delegate);
		setService(manager, asInterface(binder_proxy));
		setContext(manager, context);
		return binder_proxy;
	}

	private IInterface getService(final T manager) {
		try {
			return (IInterface) mService.get(manager);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	private void setService(final T manager, final IInterface service) {
		try {
			mService.set(manager, service);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	private void setContext(final T manager, final Context context) {
		if (mContext != null) try {
			mContext.set(manager, context);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	private IInterface asInterface(final IBinder binder) throws ReflectiveOperationException {
		return (IInterface) Class.forName(mService.getType().getName() + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
	}

	DerivedManagerHelper(final Class<T> manager_class) {
		this(manager_class, "mService", "mContext");
	}

	private DerivedManagerHelper(final Class<T> manager_class, final String service_field, final @Nullable String context_field) {
		try {
			mService = manager_class.getDeclaredField(service_field);
			mService.setAccessible(true);
			if (context_field != null) {
				mContext = manager_class.getDeclaredField(context_field);
				mContext.setAccessible(true);
			} else mContext = null;
		} catch (final NoSuchFieldException | SecurityException e) {
			throw new UnsupportedOperationException("Incompatible ROM", e);
		}
	}

	private final Field mService;
	private final @Nullable Field mContext;
}
