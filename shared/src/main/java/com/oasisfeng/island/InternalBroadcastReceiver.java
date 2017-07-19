package com.oasisfeng.island;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;

import com.oasisfeng.island.shuttle.ActivityShuttle;

/**
 * Manifest stub for internal broadcast receivers
 *
 * Created by Oasis on 2017/7/17.
 */
public abstract class InternalBroadcastReceiver extends BroadcastReceiver {

	public static ComponentName getComponent(final Context context, final Class<?> receiver_class) {
		if (receiver_class == ActivityShuttle.class) return new ComponentName(context, _1.class);
		throw new IllegalArgumentException("Invalid receiver class: " + receiver_class.getCanonicalName());
	}

	protected ComponentName getComponent(final Context context) { return new ComponentName(context, getClass()); }

	public static final class _1 extends com.oasisfeng.island.shuttle.ActivityShuttle {}
}
