package com.oasisfeng.island;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

/**
 * Created by Oasis on 2019-4-30.
 */
public class DerivedManagerHelper<T> {

	public void setService(final T manager, final IInterface service) {
		try {
			mService.set(manager, service);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	public void setContext(final T manager, final Context context) {
		if (mContext != null) try {
			mContext.set(manager, context);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	public IInterface getService(final T manager) {
		try {
			return (IInterface) mService.get(manager);
		} catch (final IllegalAccessException e) { throw new IllegalStateException(e); }	// Should never happen
	}

	public DerivedManagerHelper(final Class<T> manager_class) {
		this(manager_class, "mService", "mContext");
	}

	public DerivedManagerHelper(final Class<T> manager_class, final String service_field, final @Nullable String context_field) {
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

	public Class getInterface() { return mService.getType(); }

	public IInterface asInterface(final IBinder binder) throws ReflectiveOperationException {
		return (IInterface) Class.forName(mService.getType().getName() + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
	}

	private final Field mService;
	private final @Nullable Field mContext;
}
