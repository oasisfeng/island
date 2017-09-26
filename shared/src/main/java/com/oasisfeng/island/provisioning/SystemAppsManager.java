package com.oasisfeng.island.provisioning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.provider.BlockedNumberContract;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony.Carriers;
import android.provider.UserDictionary;
import android.util.Log;

import com.google.common.base.Stopwatch;
import com.oasisfeng.island.engine.common.WellKnownPackages;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.ProfileUser;
import com.oasisfeng.perf.Performances;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

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
			"com.android.systemui",					// This package is generally safe to either freeze or not, leave them unfrozen for better compatibility.
			"com.android.packageinstaller",			// Package installer responsible for ACTION_INSTALL_PACKAGE (AOSP)
			"com.android.settings",					// For various setting intent activities
			"com.android.keychain",					// MIUI system will crash without this
			"com.android.providers.telephony",		// The SMS & MMS and carrier info provider. (SMS & MMS provider is shared across all users)
			"com.android.providers.contacts",		// The storage of contacts, for contacts data access.
			"com.android.providers.calllogbackup",	// The storage of call log (deprecated)
			"com.android.providers.calendar",		// The storage of calendar, for calendar data access.
			"com.android.providers.downloads",		// For DownloadManager to work
			"com.android.providers.media",			// For media access
			"com.android.externalstorage",			// Documents provider - Internal storage
			"com.android.defconatiner",				// For APK file from DownloadManager to install
			"com.android.vpndialogs",				// Required by VPN service app (e.g. ShadowSocks)
			"com.android.documentsui",				// Documents UI (part of Android Storage Access Framework)
			Hacks.PrintManager_PRINT_SPOOLER_PACKAGE_NAME.get(),	// Print spooler (a critical bug will raise if this package is disabled)
			// Enabled system apps with launcher activity by default
			WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE,			// Google Play Store to let user install apps directly within
			"com.android.contacts",					// Contacts
			"com.android.providers.downloads.ui",	// Downloads
			// Essential Google packages
			"com.google.android.gsf",				// Google services framework
			"com.google.android.packageinstaller",	// Package installer (Google)
			WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES,	// Disabling GMS in the provision will cause GMS in owner user being killed too due to its single user nature, causing weird ANR.
			"com.google.android.feedback",			// Used by GMS for crash report
			"com.google.android.contacts",			// Contacts (Google)
			// MIUI-specific
			"com.miui.core"							// Required by com.lbe.security.miui (Runtime permission UI of MIUI)
	);
	private static final Collection<Intent> sCriticalActivityIntents = Arrays.asList(
			new Intent(Intent.ACTION_INSTALL_PACKAGE),				// Usually com.android.packageinstaller, may be altered by ROM.
			new Intent(Settings.ACTION_SETTINGS),					// Usually com.android.settings
			new Intent("android.content.pm.action.REQUEST_PERMISSIONS"/* PackageManager.ACTION_REQUEST_PERMISSIONS */),
																	// Runtime permission UI, may be special system app on some ROMs (e.g. MIUI)
			/* New entrance for Downloads UI, required by old Downloads app trampoline */
			new Intent("android.provider.action.MANAGE_ROOT"/* DocumentsContract.ACTION_MANAGE_ROOT */,
					DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads")),
			new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),	// Usually com.android.documentsui, may be file explorer app on some ROMs. (e.g. MIUI)
			new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI)	// Contact picker, usually com.android.contacts
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

//	/** This is generally not necessary on AOSP but will eliminate various problems on custom ROMs */
//	private void disableNonCriticalSystemPackages() {
//		final PackageManager pm = mContext.getPackageManager();
//		final Set<String> critical_sys_pkgs = detectCriticalSystemPackages(pm, mIslandManager, 0);
//		for (final ApplicationInfo app : pm.getInstalledApplications(0)) {
//			if ((app.flags & FLAG_SYSTEM) == 0 || critical_sys_pkgs.contains(app.packageName)) continue;
//			if (! hasSingleUserComponent(pm, app.packageName)) {
//				Log.i(TAG, "Disable non-critical system app: " + app.packageName);
//				mIslandManager.freezeApp(app.packageName, "provision");
//			} else Log.i(TAG, "Not disabling system app capable for multi-user: " + app.packageName);
//		}
//	}

	/** @param flags intersection of {@code PackageManager.ComponentInfoFlags} and {@code PackageManager.ResolveInfoFlags}. */
	@OwnerUser @ProfileUser public static Set<String> detectCriticalSystemPackages(final PackageManager pm, final int flags) {
		final Stopwatch stopwatch = Performances.startUptimeStopwatch();

		final Set<String> critical_sys_pkgs = new HashSet<>(sCriticalSystemPkgs);

		// Detect package names for critical intent actions, as an addition to the white-list of well-known ones.
		for (final Intent intent : sCriticalActivityIntents) {
			@SuppressLint("WrongConstant") final List<String> pkgs =
					StreamSupport.stream(pm.queryIntentActivities(intent, MATCH_DEFAULT_ONLY | Hacks.MATCH_ANY_USER_AND_UNINSTALLED))
					.filter(info -> (info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) != 0)
					.map(info -> info.activityInfo.packageName)
					.collect(Collectors.toList());
			Log.i(TAG, "Critical package(s) for " + intent + ": " + pkgs);
			critical_sys_pkgs.addAll(pkgs);
		}
		Performances.check(stopwatch, 1, "CriticalActivities");

		// Detect package names for critical content providers, as an addition to the white-list of well-known ones.
		for (final String authority : sCriticalContentAuthorities) {
			if (authority == null) continue;		// Nullable for version-specific authorities
			final ProviderInfo provider = pm.resolveContentProvider(authority, flags);
			if (provider == null || (provider.applicationInfo.flags & FLAG_SYSTEM) == 0 || (provider.flags & FLAG_SINGLE_USER) != 0) continue;
			Log.i(TAG, "Critical package for authority \"" + authority + "\": " + provider.packageName);
			critical_sys_pkgs.add(provider.packageName);
		}
		Performances.check(stopwatch, 1, "CriticalProviders");

		// Detect non-launchable system components with sync adapter (never use ContentResolver.getSyncAdapterTypes() which only returns unfrozen adapters)
		final List<ResolveInfo> adapters = pm.queryIntentServices(new Intent("android.content.SyncAdapter"), flags);
		final Intent launch_intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		String last_pkg = null;
		for (final ResolveInfo resolved : adapters) {
			final ServiceInfo adapter = resolved.serviceInfo;
			if ((adapter.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;		// Only system apps
			final String pkg = adapter.packageName;
			if (pkg.equals(last_pkg)) continue;
			if (pm.resolveActivity(launch_intent.setPackage(pkg), flags) == null) {
				Log.i(TAG, "Critical package for sync-adapter: " + pkg);		// Only if not launchable package.
				critical_sys_pkgs.add(pkg);
			}
			last_pkg = pkg;		// Skip all other adapters in the same package.
		}
		Performances.check(stopwatch, 1, "CriticalSyncAdapters");

		return critical_sys_pkgs;
	}

	private static boolean hasSingleUserComponent(final PackageManager pm, final String pkg) {
		try {
			final PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
			if (info.services != null) for (final ServiceInfo service : info.services)
				if ((service.flags & ServiceInfo.FLAG_SINGLE_USER) != 0) return true;
			if (info.providers != null) for (final ProviderInfo service : info.providers)
				if ((service.flags & ProviderInfo.FLAG_SINGLE_USER) != 0) return true;
			return false;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	SystemAppsManager(final Context context) {
		mContext = context;
	}

	private final Context mContext;
	private static final String TAG = "Island.SysApps";
}
