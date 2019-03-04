package com.oasisfeng.island.appops;

import android.app.AppOpsManager;
import android.app.AppOpsManager$PackageOps;
import android.content.Context;
import android.util.Log;

import com.oasisfeng.island.util.Hacks;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static java.util.Objects.requireNonNull;

/**
 * Created by Oasis on 2019-3-1.
 */
public class AppOpsCompat {

	public static final String GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";

	@RequiresPermission(GET_APP_OPS_STATS) @SuppressWarnings("unchecked")
	@Nullable List<AppOpsManager$PackageOps> getOpsForPackage(final int uid, final String pkg, final int[] ops) {
		if (Hacks.AppOpsManager_getOpsForPackage.isAbsent()) return null;
		return Hacks.AppOpsManager_getOpsForPackage.invoke(uid, pkg, ops).on(mAppOpsManager);
	}

	void setMode(final int code, final int uid, final String pkg, final int mode) {
		if (Hacks.AppOpsManager_setMode.isAbsent()) return;
		Hacks.AppOpsManager_setMode.invoke(code, uid, pkg, mode).on(mAppOpsManager);
	}

	static int opToDefaultMode(final int op) {
		return sOpDefaultMode[op];
	}

	public AppOpsCompat(final Context context) {
		mAppOpsManager = (AppOpsManager) requireNonNull(context.getSystemService(Context.APP_OPS_SERVICE));
	}

	private final AppOpsManager mAppOpsManager;

	private static final String TAG = "Island.AOC";

	static {
		try {
			final int[] value = Hacks.AppOpsManager_sOpDefaultMode.get();
			if (value != null) sOpDefaultMode = value;
		} catch (final RuntimeException e) {
			Log.e(TAG, "Error correcting sOpDefaultMode", e);
		}
	}

	/** (Mirrored from {@link AppOpsManager}) This specifies the default mode for each operation. */
	private static int[] sOpDefaultMode = new int[] {
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_IGNORED, // OP_WRITE_SMS
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			SDK_INT < M ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT, // OP_WRITE_SETTINGS
			SDK_INT < M ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT, // OP_SYSTEM_ALERT_WINDOW
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_DEFAULT, // OP_GET_USAGE_STATS
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_IGNORED, // OP_PROJECT_MEDIA
			AppOpsManager.MODE_IGNORED, // OP_ACTIVATE_VPN
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ERRORED,  // OP_MOCK_LOCATION
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,  // OP_TURN_ON_SCREEN
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_ALLOWED,  // OP_RUN_IN_BACKGROUND
			AppOpsManager.MODE_ALLOWED,  // OP_AUDIO_ACCESSIBILITY_VOLUME
			AppOpsManager.MODE_ALLOWED,
			AppOpsManager.MODE_DEFAULT,  // OP_REQUEST_INSTALL_PACKAGES
			AppOpsManager.MODE_ALLOWED,  // OP_PICTURE_IN_PICTURE
			AppOpsManager.MODE_DEFAULT,  // OP_INSTANT_APP_START_FOREGROUND
			AppOpsManager.MODE_ALLOWED,  // ANSWER_PHONE_CALLS
			AppOpsManager.MODE_ALLOWED,  // OP_RUN_ANY_IN_BACKGROUND
			AppOpsManager.MODE_ALLOWED,  // OP_CHANGE_WIFI_STATE
			AppOpsManager.MODE_ALLOWED,  // REQUEST_DELETE_PACKAGES
			AppOpsManager.MODE_ALLOWED,  // OP_BIND_ACCESSIBILITY_SERVICE
			AppOpsManager.MODE_ALLOWED,  // ACCEPT_HANDOVER
			AppOpsManager.MODE_ERRORED,  // MANAGE_IPSEC_TUNNELS
			AppOpsManager.MODE_ALLOWED,  // OP_START_FOREGROUND
			AppOpsManager.MODE_ALLOWED,  // OP_BLUETOOTH_SCAN
	};
}
