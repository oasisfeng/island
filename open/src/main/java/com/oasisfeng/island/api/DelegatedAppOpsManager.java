package com.oasisfeng.island.api;

import android.app.AppOpsManager;
import android.app.DerivedAppOpsManager;
import android.content.Context;
import android.os.IBinder;

import com.oasisfeng.island.RestrictedBinderProxy;

import static android.content.Context.APP_OPS_SERVICE;

/**
 * Delegated {@link AppOpsManager}
 *
 * Created by Oasis on 2019-4-30.
 */
public class DelegatedAppOpsManager extends DerivedAppOpsManager {

	/** Must only be called in isolated process */
	public DelegatedAppOpsManager(final Context context, final IBinder binder) throws ReflectiveOperationException {
		super(context, sHelper.asInterface(binder));
	}

	public DelegatedAppOpsManager(final Context context) throws ReflectiveOperationException {
		super(context, sHelper.asInterface(new RestrictedBinderProxy(sHelper.getService((AppOpsManager) context.getSystemService(APP_OPS_SERVICE)))));
	}

	public RestrictedBinderProxy getDelegatedBinderProxy() {
		return (RestrictedBinderProxy) sHelper.getService(this).asBinder();
	}
}
