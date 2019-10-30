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

	public static final int OP_COARSE_LOCATION = 0;
	public static final int OP_POST_NOTIFICATION = 11;
	public static final int OP_READ_SMS = 14;
	public static final int OP_SYSTEM_ALERT_WINDOW = 24;
	public static final int OP_READ_PHONE_STATE = 51;
	public static final int OP_REQUEST_INSTALL_PACKAGES = 66;

	public static final String OPSTR_RUN_IN_BACKGROUND = "android:run_in_background";
	public static final String OPSTR_REQUEST_INSTALL_PACKAGES = "android:request_install_packages";
	public static final String OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";

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
		return mAppOpsManager.getOpsForPackage(uid, pkg, ops);
	}

	/**
	 * Retrieve current operation state for all applications. (across all users, with differential uid)
	 *
	 * @param ops The set of operations you are interested in, or null if you want all of them.
	 */
	@Nullable List<Hacks.AppOpsManager.PackageOps> getPackagesForOps(final @Nullable int[] ops) {
		return mAppOpsManager.getPackagesForOps(ops);
	}

	@RequiresApi(28) public void setMode(final int code, final int uid, final String pkg, final int mode) {
		mAppOpsManager.setMode(code, uid, pkg, mode);
	}

	/** @return -1 for incompatibility */
	public int checkOpNoThrow(final int op, final int uid, final String pkg) {
		return mAppOpsManager.checkOpNoThrow(op, uid, pkg);
	}

	public int opToDefaultMode(final int op) {
		final int default_mode = mAppOpsManager.opToDefaultMode(op);
		if (default_mode >= 0) return default_mode;
		return sOpDefaultMode[op];	// Fallback local map
	}

	public AppOpsCompat(final Context context) {
		this((AppOpsManager) requireNonNull(context.getSystemService(Context.APP_OPS_SERVICE)));
	}

	public AppOpsCompat(final AppOpsManager manager) {
		mAppOpsManager = Hack.into(manager).with(Hacks.AppOpsManager.class);
	}

	AppOpsCompat(final Hacks.AppOpsManager mock) { mAppOpsManager = mock; }

	private final Hacks.AppOpsManager mAppOpsManager;

	/** (Mirrored from {@link AppOpsManager}) This specifies the default mode for each operation. */
	private static final int[] sOpDefaultMode = new int[] {
			AppOpsManager.MODE_ALLOWED, // COARSE_LOCATION
			AppOpsManager.MODE_ALLOWED, // FINE_LOCATION
			AppOpsManager.MODE_ALLOWED, // GPS
			AppOpsManager.MODE_ALLOWED, // VIBRATE
			AppOpsManager.MODE_ALLOWED, // READ_CONTACTS
			AppOpsManager.MODE_ALLOWED, // WRITE_CONTACTS
			AppOpsManager.MODE_ALLOWED, // READ_CALL_LOG
			AppOpsManager.MODE_ALLOWED, // WRITE_CALL_LOG
			AppOpsManager.MODE_ALLOWED, // READ_CALENDAR
			AppOpsManager.MODE_ALLOWED, // WRITE_CALENDAR
			AppOpsManager.MODE_ALLOWED, // WIFI_SCAN
			AppOpsManager.MODE_ALLOWED, // POST_NOTIFICATION
			AppOpsManager.MODE_ALLOWED, // NEIGHBORING_CELLS
			AppOpsManager.MODE_ALLOWED, // CALL_PHONE
			AppOpsManager.MODE_ALLOWED, // READ_SMS
			AppOpsManager.MODE_IGNORED, // WRITE_SMS
			AppOpsManager.MODE_ALLOWED, // RECEIVE_SMS
			AppOpsManager.MODE_ALLOWED, // RECEIVE_EMERGENCY_BROADCAST
			AppOpsManager.MODE_ALLOWED, // RECEIVE_MMS
			AppOpsManager.MODE_ALLOWED, // RECEIVE_WAP_PUSH
			AppOpsManager.MODE_ALLOWED, // SEND_SMS
			AppOpsManager.MODE_ALLOWED, // READ_ICC_SMS
			AppOpsManager.MODE_ALLOWED, // WRITE_ICC_SMS
			SDK_INT < M ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT, // OP_WRITE_SETTINGS
			SDK_INT < M ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT, // OP_SYSTEM_ALERT_WINDOW  TODO: MODE_IGNORED on low ram phones on Q+
			AppOpsManager.MODE_ALLOWED, // ACCESS_NOTIFICATIONS
			AppOpsManager.MODE_ALLOWED, // CAMERA
			AppOpsManager.MODE_ALLOWED, // RECORD_AUDIO
			AppOpsManager.MODE_ALLOWED, // PLAY_AUDIO
			AppOpsManager.MODE_ALLOWED, // READ_CLIPBOARD
			AppOpsManager.MODE_ALLOWED, // WRITE_CLIPBOARD
			AppOpsManager.MODE_ALLOWED, // TAKE_MEDIA_BUTTONS
			AppOpsManager.MODE_ALLOWED, // TAKE_AUDIO_FOCUS
			AppOpsManager.MODE_ALLOWED, // AUDIO_MASTER_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_VOICE_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_RING_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_MEDIA_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_ALARM_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_NOTIFICATION_VOLUME
			AppOpsManager.MODE_ALLOWED, // AUDIO_BLUETOOTH_VOLUME
			AppOpsManager.MODE_ALLOWED, // WAKE_LOCK
			AppOpsManager.MODE_ALLOWED, // MONITOR_LOCATION
			AppOpsManager.MODE_ALLOWED, // MONITOR_HIGH_POWER_LOCATION
			AppOpsManager.MODE_DEFAULT, // GET_USAGE_STATS
			AppOpsManager.MODE_ALLOWED, // MUTE_MICROPHONE
			AppOpsManager.MODE_ALLOWED, // TOAST_WINDOW
			AppOpsManager.MODE_IGNORED, // PROJECT_MEDIA
			AppOpsManager.MODE_IGNORED, // ACTIVATE_VPN
			AppOpsManager.MODE_ALLOWED, // WRITE_WALLPAPER
			AppOpsManager.MODE_ALLOWED, // ASSIST_STRUCTURE
			AppOpsManager.MODE_ALLOWED, // ASSIST_SCREENSHOT
			AppOpsManager.MODE_ALLOWED, // READ_PHONE_STATE
			AppOpsManager.MODE_ALLOWED, // ADD_VOICEMAIL
			AppOpsManager.MODE_ALLOWED, // USE_SIP
			AppOpsManager.MODE_ALLOWED, // PROCESS_OUTGOING_CALLS
			AppOpsManager.MODE_ALLOWED, // USE_FINGERPRINT
			AppOpsManager.MODE_ALLOWED, // BODY_SENSORS
			AppOpsManager.MODE_ALLOWED, // READ_CELL_BROADCASTS
			AppOpsManager.MODE_ERRORED, // MOCK_LOCATION
			AppOpsManager.MODE_ALLOWED, // READ_EXTERNAL_STORAGE
			AppOpsManager.MODE_ALLOWED, // WRITE_EXTERNAL_STORAGE
			AppOpsManager.MODE_ALLOWED, // TURN_SCREEN_ON
			AppOpsManager.MODE_ALLOWED, // GET_ACCOUNTS
			AppOpsManager.MODE_ALLOWED, // RUN_IN_BACKGROUND
			AppOpsManager.MODE_ALLOWED, // AUDIO_ACCESSIBILITY_VOLUME
			AppOpsManager.MODE_ALLOWED, // READ_PHONE_NUMBERS
			AppOpsManager.MODE_DEFAULT, // REQUEST_INSTALL_PACKAGES
			AppOpsManager.MODE_ALLOWED, // PICTURE_IN_PICTURE
			AppOpsManager.MODE_DEFAULT, // INSTANT_APP_START_FOREGROUND
			AppOpsManager.MODE_ALLOWED, // ANSWER_PHONE_CALLS
			AppOpsManager.MODE_ALLOWED, // RUN_ANY_IN_BACKGROUND
			AppOpsManager.MODE_ALLOWED, // CHANGE_WIFI_STATE
			AppOpsManager.MODE_ALLOWED, // REQUEST_DELETE_PACKAGES
			AppOpsManager.MODE_ALLOWED, // BIND_ACCESSIBILITY_SERVICE
			AppOpsManager.MODE_ALLOWED, // ACCEPT_HANDOVER
			AppOpsManager.MODE_ERRORED, // MANAGE_IPSEC_TUNNELS
			AppOpsManager.MODE_ALLOWED, // START_FOREGROUND
			AppOpsManager.MODE_ALLOWED, // BLUETOOTH_SCAN
			AppOpsManager.MODE_ALLOWED, // USE_BIOMETRIC
			AppOpsManager.MODE_ALLOWED, // ACTIVITY_RECOGNITION
			AppOpsManager.MODE_DEFAULT, // SMS_FINANCIAL_TRANSACTIONS
			AppOpsManager.MODE_ALLOWED, // READ_MEDIA_AUDIO
			AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_AUDIO
			AppOpsManager.MODE_ALLOWED, // READ_MEDIA_VIDEO
			AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_VIDEO
			AppOpsManager.MODE_ALLOWED, // READ_MEDIA_IMAGES
			AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_IMAGES
			AppOpsManager.MODE_DEFAULT, // LEGACY_STORAGE
			AppOpsManager.MODE_ALLOWED, // ACCESS_ACCESSIBILITY
			AppOpsManager.MODE_ERRORED, // READ_DEVICE_IDENTIFIERS
	};
}
