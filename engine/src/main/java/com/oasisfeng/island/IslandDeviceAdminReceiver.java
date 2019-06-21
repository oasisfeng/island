package com.oasisfeng.island;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.oasisfeng.island.provisioning.IslandProvisioning;
import com.oasisfeng.island.util.ProfileUser;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;

/**
 * Handles events related to managed profile.
 */
public class IslandDeviceAdminReceiver extends DeviceAdminReceiver {

    @ProfileUser @Override public void onProfileProvisioningComplete(final Context context, final Intent intent) {
        // DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL is used instead of this trigger on Android O+.
        if (SDK_INT < O) IslandProvisioning.onProfileProvisioningComplete(context, intent);
    }
}
