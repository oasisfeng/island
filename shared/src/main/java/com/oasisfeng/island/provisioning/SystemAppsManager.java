package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.BlockedNumberContract;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony.Carriers;
import android.provider.UserDictionary;
import android.util.Log;

import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.perf.Performances;
import com.oasisfeng.perf.Stopwatch;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.ProviderInfo.FLAG_SINGLE_USER;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Manage the critical system apps in Island
 *
 * Created by Oasis on 2016/4/26.
 */
@ProfileUser public class SystemAppsManager {

	/** This list serves as a known common package names for quick filtering */
	private static final Collection<String> sCriticalSystemPkgs = Arrays.asList(
			"android",
			"com.android.settings",					// For various setting intent activities
			"com.android.keychain",					// MIUI system will crash without this
			"com.android.providers.telephony",		// The SMS & MMS and carrier info provider. (SMS & MMS provider is shared across all users)
			"com.android.providers.contacts",		// The storage of contacts, for contacts data access.
			"com.android.providers.calllogbackup",	// The storage of call log (deprecated)
			"com.android.providers.calendar",		// The storage of calendar, for calendar data access.
			"com.android.providers.downloads",		// For DownloadManager to work
			"com.android.providers.media",			// For media access
			"com.android.providers.userdictionary",	// For IMEs and Settings, the latter of which will crash without this.
			"com.android.externalstorage",			// Documents provider - Internal storage
			"com.android.defconatiner",				// For APK file from DownloadManager to install
			"com.android.vpndialogs",				// Required by VPN service app (e.g. ShadowSocks)
			"com.android.documentsui",				// Documents UI (part of Android Storage Access Framework)
			Hacks.PrintManager_PRINT_SPOOLER_PACKAGE_NAME.get(),	// Print spooler (a critical bug will raise if this package is disabled)
			// Enabled system apps with launcher activity by default
			WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE,			// Google Play Store to let user install apps directly within
			"com.android.providers.downloads.ui",	// Downloads
			// Essential Google packages
			"com.google.android.gsf",				// Google services framework
			WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES,	// Disabling GMS in the provision will cause GMS in owner user being killed too due to its single user nature, causing weird ANR.
			"com.google.android.feedback"			// Used by GMS for crash report
	);
	private static final Collection<Intent> sCriticalActivityIntents = Arrays.asList(
			new Intent(Intent.ACTION_INSTALL_PACKAGE)				// Usually com.[google.]android.packageinstaller, may be altered by ROM.
					.setData(Uri.fromParts("file", "dummy.apk", null)),
			new Intent(Settings.ACTION_SETTINGS),					// Usually com.android.settings
			new Intent("android.content.pm.action.REQUEST_PERMISSIONS"/* PackageManager.ACTION_REQUEST_PERMISSIONS */),
																	// Runtime permission UI, may be special system app on some ROMs (e.g. MIUI)
			/* New entrance for Downloads UI, required by old Downloads app trampoline */
			new Intent("android.provider.action.MANAGE_ROOT"/* DocumentsContract.ACTION_MANAGE_ROOT */,
					DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads")),
			new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")	// Usually com.android.documentsui, may be file explorer app on some ROMs. (e.g. MIUI)
	);
	private static final Collection<String> sCriticalContentAuthorities = Arrays.asList(
			ContactsContract.AUTHORITY,								// Usually com.android.providers.contacts
			CallLog.AUTHORITY,										// Usually com.android.providers.contacts (originally com.android.providers.calllogbackup)
			CalendarContract.AUTHORITY,								// Usually com.android.providers.calendar
			Carriers.CONTENT_URI.getAuthority(),					// Usually com.android.providers.telephony
			MediaStore.AUTHORITY,									// Usually com.android.providers.media
			SDK_INT >= N ? BlockedNumberContract.AUTHORITY : null,	// Usually com.android.providers.blockednumber (required by phone app)
			"downloads",											// Usually com.android.providers.downloads
			UserDictionary.AUTHORITY,								// Usually com.android.providers.userdictionary
			"com.android.providers.downloads.documents",			// Newer authority of com.android.providers.downloads
			"com.android.externalstorage.documents",				// Usually com.android.externalstorage
			"logs"													// Samsung-specific voice-mail content provider (content://logs/from_vvm)
	);

	/**
	 * Determine whether a package is a "system package", in which case certain things
	 * (like disabling notifications or disabling the package altogether) should be disallowed.
	 */
	public boolean isSystemSignature(final PackageInfo pkg) {
		if (sSystemSignature == null) {
			sSystemSignature = new Signature[] { getSystemSignature(mContext.getPackageManager()) };
		}
		return sSystemSignature[0] != null && sSystemSignature[0].equals(getFirstSignature(pkg));
	}

	private static Signature[] sSystemSignature;

	private static Signature getFirstSignature(final PackageInfo pkg) {
		if (pkg != null && pkg.signatures != null && pkg.signatures.length > 0)
			return pkg.signatures[0];
		return null;
	}

	@SuppressLint("PackageManagerGetSignatures") private static Signature getSystemSignature(final PackageManager pm) {
		try {
			final PackageInfo sys = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
			return getFirstSignature(sys);
		} catch (final PackageManager.NameNotFoundException ignored) { return null; }
	}

	@OwnerUser @ProfileUser public static Set<String> detectCriticalSystemPackages(final PackageManager pm) {
		final Stopwatch stopwatch = Performances.startUptimeStopwatch();

		final Set<String> critical_sys_pkgs = new HashSet<>(sCriticalSystemPkgs);

		// Detect package names for critical intent actions, as an addition to the white-list of well-known ones.
		for (final Intent intent : sCriticalActivityIntents) try { @SuppressLint("WrongConstant")
			final ResolveInfo info = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY | Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED);
			if (info == null || (info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) == 0) continue;
			final String pkg = info.activityInfo.packageName;
			Log.i(TAG, "Critical package for " + intent + ": " + pkg);
			critical_sys_pkgs.add(pkg);
		} catch (final RuntimeException e) {	// NPE on Android 6 for some ASUS devices (e.g. ZenFone 2)
			Analytics.$().report(e);
		}
		Performances.check(stopwatch, 1, "CriticalActivities");

		// Detect package names for critical content providers, as an addition to the white-list of well-known ones.
		for (final String authority : sCriticalContentAuthorities) {
			if (authority == null) continue;		// Nullable for version-specific authorities
			@SuppressLint("WrongConstant") final ProviderInfo provider = pm.resolveContentProvider(authority, Hacks.RESOLVE_ANY_USER_AND_UNINSTALLED);
			if (provider == null || (provider.applicationInfo.flags & FLAG_SYSTEM) == 0 || (provider.flags & FLAG_SINGLE_USER) != 0) continue;
			Log.i(TAG, "Critical package for authority \"" + authority + "\": " + provider.packageName);
			critical_sys_pkgs.add(provider.packageName);
		}
		Performances.check(stopwatch, 1, "CriticalProviders");

		return critical_sys_pkgs;
	}

	SystemAppsManager(final Context context) {
		mContext = context;
	}

	private final Context mContext;
	private static final String TAG = "Island.SysApps";
}
