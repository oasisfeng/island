package com.oasisfeng.island;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.oasisfeng.island.provisioning.IslandProvisioning;

/**
 * Handles events related to managed profile.
 */
public class IslandDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override public void onProfileProvisioningComplete(final Context context, final Intent intent) {
        IslandProvisioning.onProfileProvisioningComplete(context, intent);
    }
}
