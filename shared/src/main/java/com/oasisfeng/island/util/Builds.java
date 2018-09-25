package com.oasisfeng.island.util;

import static android.os.Build.VERSION.PREVIEW_SDK_INT;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O_MR1;

/**
 * Helper for {@link android.os.Build}-related functionality
 *
 * Created by Oasis on 2018-9-25.
 */
public class Builds {

	public static boolean isAndroidPIncludingPreviews() {
		return SDK_INT > O_MR1 || (SDK_INT == O_MR1 && PREVIEW_SDK_INT > 0);
	}
}
