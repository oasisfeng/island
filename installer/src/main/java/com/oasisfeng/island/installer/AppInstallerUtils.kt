package com.oasisfeng.island.installer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Parcelable
import androidx.annotation.RequiresApi
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Hacks
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@SuppressLint("InlinedApi") internal const val INVALID_SESSION_ID = PackageInstaller.SessionInfo.INVALID_ID
internal const val EXTRA_INSTALL_INFO = "install"

internal object AppInstallerUtils {

	@JvmStatic fun ensureSystemPackageEnabledAndUnfrozen(context: Context, intent: Intent): Boolean {
		val resolve = context.packageManager.resolveActivity(intent, MATCH_UNINSTALLED_PACKAGES or MATCH_SYSTEM_ONLY)
				?: return false
		if (Apps.isInstalledInCurrentUser(resolve.activityInfo.applicationInfo)) return true
		return DevicePolicies(context).run { isProfileOwner && (enableSystemAppByIntent(intent)
				|| IslandManager.ensureAppFreeToLaunch(context, resolve.activityInfo.packageName).isEmpty()) }
	}

	@JvmStatic fun ApplicationInfo.hasRequestedLegacyExternalStorage() = SDK_INT == Q
			&& PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE.let { Hacks.ApplicationInfo_privateFlags.get(this)?.and(it) == it }

	@RequiresApi(Q) fun ApplicationInfo.setRequestedLegacyExternalStorage() =
			Hacks.ApplicationInfo_privateFlags.set(this, PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE)

	private const val PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE = 1 shl 29       // Hidden in PackageManager
}

@Parcelize data class AppInstallInfo(val caller: String, val callerUid: Int,

                                     var mode: Mode = Mode.INSTALL,
                                     var appId: String? = null,
                                     var appLabel: CharSequence? = null,
                                     var versionName: String? = null,
                                     var targetSdkVersion: Int? = null,
                                     var requestedLegacyExternalStorage: Boolean = false,
                                     var details: CharSequence? = null): Parcelable {
	constructor(context: Context, caller: String, callerUid: Int) : this(caller, callerUid) { this.context = context }

	@IgnoredOnParcel val callerLabel: CharSequence by lazy { Apps.of(context).getAppName(caller) }
	@IgnoredOnParcel @Suppress("JoinDeclarationAndAssignment") lateinit var context: Context

	/** See [PackageInstaller.SessionParams.MODE_INHERIT_EXISTING] */
	enum class Mode { INSTALL, UPDATE, CLONE, INHERIT }
}
