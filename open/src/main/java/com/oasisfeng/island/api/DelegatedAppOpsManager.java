package com.oasisfeng.island.api;

import android.app.AppOpsManager;
import android.app.DerivedAppOpsManager;
import android.content.Context;
import android.os.Binder;

import com.oasisfeng.hack.Hack;
import com.oasisfeng.island.RestrictedBinderProxy;
import com.oasisfeng.island.util.Hacks;

import static android.content.Context.APP_OPS_SERVICE;
import static com.oasisfeng.island.ApiConstants.DELEGATION_APP_OPS;

/**
 * Delegated {@link AppOpsManager}
 *
 * Created by Oasis on 2019-4-30.
 */
public class DelegatedAppOpsManager extends DerivedAppOpsManager {

	public static Binder buildBinderProxy(final Context context) throws ReflectiveOperationException {
		return new DelegatedAppOpsManager(context).mBinderProxy;
	}

	private DelegatedAppOpsManager(final Context context) throws ReflectiveOperationException {
		mBinderProxy = sHelper.inject(this, context, APP_OPS_SERVICE, DELEGATION_APP_OPS);

		// Whiltelist supported APIs by invoking them (with dummy arguments) before seal().
		final Hacks.AppOpsManager aom = Hack.into(this).with(Hacks.AppOpsManager.class);
		aom.setMode(0, 0, "a.b.c", 0);
		aom.getOpsForPackage(0, "a.b.c", new int[]{ 0 });
		aom.getPackagesForOps(new int[]{ 0 });
		mBinderProxy.seal();
	}

	private final RestrictedBinderProxy mBinderProxy;

	private static final DerivedManagerHelper<AppOpsManager> sHelper = new DerivedManagerHelper<>(AppOpsManager.class);
}
