package com.oasisfeng.island;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;

import com.oasisfeng.island.provisioning.ManualProvisioningReceiver;

/**
 * Manifest stub for internal broadcast receivers
 *
 * Created by Oasis on 2017/7/17.
 */
public abstract class InternalBroadcastReceiver extends BroadcastReceiver {

	protected ComponentName getComponent(final Context context) { return new ComponentName(context, getClass()); }

	public static final class _1 extends ManualProvisioningReceiver {}
}
