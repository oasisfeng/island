package com.oasisfeng.island.permission;

import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import com.android.packageinstaller.permission.ui.GrantPermissionsActivity;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.oasisfeng.island.Config;
import com.oasisfeng.island.permission.mirror.PackageManager;

import java.util.ArrayList;
import java.util.List;

import static android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
import static android.os.Build.VERSION_CODES.M;

/**
 * Wrap {@link GrantPermissionsActivity} with prerequisites.
 *
 * Created by Oasis on 2017/8/15.
 */
@RequiresApi(M)
public class PermissionRequestActivity extends GrantPermissionsActivity {

	@Override public void onCreate(final Bundle icicle) {
		final String caller;
		if (! PermissionRequestSetup.setup(this) || (caller = getCallingPackage()) != null	// The case "caller == null" is handled in super.onCreate()
				&& ! Iterables.contains(Splitter.on(',').split(Config.PERMISSION_REQUEST_ALLOWED_APPS.get()), caller))
			getIntent().removeExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);    // Make super.onCreate() finish immediately.
		super.onCreate(icicle);
	}

	@Override protected String[] getSupportedPermissions() {
		try {
			final List<PermissionInfo> permissions = getPackageManager().queryPermissionsByGroup(null, 0);
			final List<String> dev_permissions = new ArrayList<>(16);
			for (final PermissionInfo permission : permissions)
				if ((permission.protectionLevel & (PROTECTION_FLAG_DEVELOPMENT | PROTECTION_FLAG_APPOP)) == PROTECTION_FLAG_DEVELOPMENT)
					dev_permissions.add(permission.name);		// Development but not AppOps
			return dev_permissions.toArray(new String[dev_permissions.size()]);
		} catch (final android.content.pm.PackageManager.NameNotFoundException e) {
			return new String[0];
		}
	}
}
