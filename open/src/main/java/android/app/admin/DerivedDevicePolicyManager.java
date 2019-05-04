package android.app.admin;

import android.content.Context;
import android.os.IInterface;

import com.oasisfeng.island.DerivedManagerHelper;

/**
 * Intermediate class for {@link DevicePolicyManager} derivation
 *
 * Created by Oasis on 2019-4-28.
 */
public class DerivedDevicePolicyManager extends DevicePolicyManager {

	protected DerivedDevicePolicyManager(final Context context, final IInterface service) {
		sHelper.setService(this, service);
		sHelper.setContext(this, context);
	}

	protected static final DerivedManagerHelper<DevicePolicyManager> sHelper = new DerivedManagerHelper<>(DevicePolicyManager.class);
}
