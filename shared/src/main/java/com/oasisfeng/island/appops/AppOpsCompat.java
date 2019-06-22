package com.oasisfeng.island.appops;

import android.app.AppOpsManager;
import android.content.Context;

import com.oasisfeng.hack.Hack;
import com.oasisfeng.island.util.Hacks;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static java.util.Objects.requireNonNull;

/**
 * Created by Oasis on 2019-3-1.
 */
public class AppOpsCompat {

	public static final int OP_POST_NOTIFICATION = 11;
	public static final int OP_SYSTEM_ALERT_WINDOW = 24;
	public static final int OP_REQUEST_INSTALL_PACKAGES = 66;
	public static final String GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";

	/**
	 * Retrieve current operation state for one application.
	 *
	 * @param uid The uid of the application of interest.
	 * @param pkg The name of the application of interest.
	 * @param ops The set of operations you are interested in, or null if you want all of them.
	 */
	@RequiresPermission(GET_APP_OPS_STATS)
	@Nullable List<Hacks.AppOpsManager.PackageOps> getOpsForPackage(final int uid, final String pkg, final @Nullable int[] ops) {
		return Hack.into(mAppOpsManager).with(Hacks.AppOpsManager.class).getOpsForPackage(uid, pkg, ops);
	}

	@RequiresApi(28) public void setMode(final int code, final int uid, final String pkg, final int mode) {
		Hack.into(mAppOpsManager).with(Hacks.AppOpsManager.class).setMode(code, uid, pkg, mode);
	}

	/** @return -1 for incompatibility */
	public int checkOpNoThrow(final int op, final int uid, final String pkg) {
		return Hack.into(mAppOpsManager).with(Hacks.AppOpsManager.class).checkOpNoThrow(op, uid, pkg);
	}

	public static int opToDefaultMode(final int op) {
		final int default_mode = Hacks.AppOpsManager.opToDefaultMode(op);
		if (default_mode >= 0) return default_mode;
		return sOpDefaultMode[op];	// Fallback local map
	}

	public AppOpsCompat(final Context context) {
		mAppOpsManager = (AppOpsManager) requireNonNull(context.getSystemService(Context.APP_OPS_SERVICE));
	}

	private final AppOpsManager mAppOpsManager;

	private static final String TAG = "Island.AOC";

	/** (Mirrored from {@link AppOpsManager}) This specifies the default mode for each operation. */
	private static final int[] sOpDefaultMode = new int[] {
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
