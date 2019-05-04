package com.oasisfeng.island.api;

import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerWrapper;
import android.os.IBinder;

import com.oasisfeng.island.DerivedManagerHelper;

import androidx.annotation.NonNull;

/**
 * Delegated {@link PackageManager}, solely for delegated {@link PackageInstaller}
 *
 * Created by Oasis on 2019-5-2.
 */
public class DelegatedPackageManager extends PackageManagerWrapper {

	@Override public @NonNull PackageInstaller getPackageInstaller() {
		return mDelegatedPackageInstaller;
	}

	public static IBinder getPackageInstallerBinder(final PackageInstaller installer) {
		return sHelper.getService(installer).asBinder();
	}

	public DelegatedPackageManager(final PackageManager base, final IBinder installer_binder) throws ReflectiveOperationException {
		super(base);
		sHelper.setService(mDelegatedPackageInstaller = base.getPackageInstaller(), sHelper.asInterface(installer_binder));
	}

	private final PackageInstaller mDelegatedPackageInstaller;

	private static final DerivedManagerHelper<PackageInstaller> sHelper = new DerivedManagerHelper<>(PackageInstaller.class, "mInstaller", null);
}
