package android.app;

import android.content.Context;
import android.os.IInterface;

import com.oasisfeng.island.DerivedManagerHelper;

/**
 * Intermediate class for {@link AppOpsManager} derivation
 *
 * Created by Oasis on 2019-4-30.
 */
public class DerivedAppOpsManager extends AppOpsManager {

	protected DerivedAppOpsManager(final Context context, final IInterface service) {
		sHelper.setContext(this, context);
		sHelper.setService(this, service);
	}

	protected static final DerivedManagerHelper<AppOpsManager> sHelper = new DerivedManagerHelper<>(AppOpsManager.class);
}
