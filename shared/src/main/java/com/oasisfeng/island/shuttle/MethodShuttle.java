package com.oasisfeng.island.shuttle;

import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.oasisfeng.android.service.AidlService;
import com.oasisfeng.android.service.Services;
import com.oasisfeng.android.util.Consumer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Shuttle for general method invocation.
 *
 * Created by Oasis on 2017/3/31.
 */
public class MethodShuttle {

	public interface GeneralMethod<ReturnType> { ReturnType invoke(); }
	public interface GeneralMethod1<ReturnType, Param1> { ReturnType invoke(Param1 param1); }
	public interface GeneralMethod2<ReturnType, Param1, Param2> { ReturnType invoke(Param1 param1, Param2 param2); }

	public <Result> void runInProfile(final GeneralMethod<Result> method, final @Nullable Consumer<Result> consumer) {
		runInProfile(method.getClass(), null, consumer);
	}

	public <Result, P1> void runInProfile(final GeneralMethod1<Result, P1> method, final P1 param1, final Consumer<Result> consumer) {
		runInProfile(method.getClass(), new Object[] { param1 }, consumer);
	}

	public <Result, P1, P2> void runInProfile(final GeneralMethod2<Result, P1, P2> method, final P1 param1, final P2 param2, final Consumer<Result> consumer) {
		runInProfile(method.getClass(), new Object[] { param1, param2 }, consumer);
	}

	private <Result> void runInProfile(final Class<?> clazz, final @Nullable Object[] args, final @Nullable Consumer<Result> consumer) {
		final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		if (constructors == null || constructors.length != 1) throw new IllegalArgumentException("The method must have exactly one constructor");
		final Constructor<?> constructor = constructors[0];
		final Class<?>[] constructor_params = constructor.getParameterTypes();
		if (constructor_params.length > 0 && constructor_params[0] != Context.class)	// Context is accepted as a special constructor parameter
			throw new IllegalArgumentException("The method must either have default constructor or have exact one Context parameter");
		final MethodInvocation<Result> invocation = new MethodInvocation<>();
		invocation.clazz = clazz.getName();
		invocation.args = args;
		Services.use(new ShuttleContext(mContext), IMethodShuttle.class, IMethodShuttle.Stub::asInterface, shuttle -> {
			shuttle.invoke(invocation);
			if (consumer != null) consumer.accept(invocation.result);
		});
	}

	public MethodShuttle(final Context context) {
		mContext = context;
	}

	public static class Service extends AidlService<IMethodShuttle.Stub> {

		@Nullable @Override protected IMethodShuttle.Stub createBinder() {
			return new IMethodShuttle.Stub() {
				@Override public void invoke(final MethodInvocation invocation) throws RemoteException {
					try {
						final Class<?> clazz = Class.forName(invocation.clazz);
						final Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
						constructor.setAccessible(true);
						final boolean need_context = constructor.getParameterTypes().length > 0;
						final Object instance = need_context ? constructor.newInstance(Service.this) : constructor.newInstance();
						if (instance instanceof GeneralMethod) //noinspection unchecked
							invocation.result = ((GeneralMethod) instance).invoke();
						else if (instance instanceof GeneralMethod1 && invocation.args != null) //noinspection unchecked
							invocation.result = ((GeneralMethod1) instance).invoke(invocation.args[0]);
						else if (instance instanceof GeneralMethod2 && invocation.args != null) //noinspection unchecked
							invocation.result = ((GeneralMethod2) instance).invoke(invocation.args[0], invocation.args[1]);
						else throw new IllegalArgumentException("Internal error: method mismatch");
					} catch (final ClassNotFoundException e) {
						throw new RemoteException("Class not found: " + invocation.clazz);
					} catch (final IllegalAccessException e) {
						throw new RemoteException("Class not accessible: " + invocation.clazz);
					} catch (final InstantiationException e) {
						throw new RemoteException("Class not instantiable: " + invocation.clazz);
					} catch (final InvocationTargetException e) {
						if (e.getTargetException() instanceof RuntimeException) throw (RuntimeException) e.getTargetException();
						throw new RemoteException("Error invoking " + invocation.clazz + ": " + e.getTargetException());
					}
				}
			};
		}
	}

	private final Context mContext;
	private static final String TAG = MethodShuttle.class.getSimpleName();
}
