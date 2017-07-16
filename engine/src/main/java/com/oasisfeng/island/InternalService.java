package com.oasisfeng.island;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;

import com.oasisfeng.island.engine.BuildConfig;
import com.oasisfeng.island.provisioning.IslandProvisioning;

/**
 * Manifest stub for internal services
 *
 * Created by Oasis on 2017/7/13.
 */
public class InternalService {

	public static abstract class InternalIntentService extends IntentService {

		protected static ComponentName getComponent(final Context context, final Class<? extends InternalIntentService> service_class) {
			if (service_class == IslandProvisioning.class) return new ComponentName(context, _1.class);
			throw new IllegalArgumentException("Invalid service class: " + service_class.getCanonicalName());
		}

		protected ComponentName getComponent() { return new ComponentName(this, getClass()); }

		protected <T extends InternalIntentService> InternalIntentService(final Class<T> service_class) {
			super(BuildConfig.DEBUG ? service_class.getSimpleName() : "Internal." + service_class.getSimpleName());
			TAG = service_class.getSimpleName();
		}

		protected final String TAG;
	}

	public static final class _1 extends IslandProvisioning {}
}
