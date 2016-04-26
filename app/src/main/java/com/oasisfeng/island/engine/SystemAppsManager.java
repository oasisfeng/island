package com.oasisfeng.island.engine;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.GET_PROVIDERS;
import static android.content.pm.PackageManager.GET_SERVICES;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.ProviderInfo.FLAG_SINGLE_USER;

/**
 * Manage the critical system apps in Island
 *
 * Created by Oasis on 2016/4/26.
 */
public class SystemAppsManager {

	private static final Collection<String> sCriticalSystemPkgs = Arrays.asList(
			"android", "com.android.systemui",		// There packages are generally safe to either freeze or not, leave them unfrozen for better compatibility.
			"com.android.packageinstaller",			// Package installer responsible for ACTION_INSTALL_PACKAGE (AOSP)
			"com.android.settings",					// For various setting intent activities
			"com.android.keychain",					// MIUI system will crash without this
			"com.android.providers.telephony",		// The SMS & MMS and carrier info provider. (SMS & MMS provider is shared across all users)
			"com.android.providers.contacts",		// The storage of contacts, for Contacts app to work
			"com.android.providers.downloads",		// For DownloadManager to work
			"com.android.providers.media",			// For media access
			"com.android.externalstorage",			// Documents provider - Internal storage
			"com.android.defconatiner",				// For APK file from DownloadManager to install.
			"com.android.webview",					// WebView provider (AOSP)
			"com.android.documentsui",				// Document picker
			// Google packages
			"com.google.android.gsf",				// Google services framework
			"com.google.android.packageinstaller",	// Package installer (Google)
			"com.google.android.gms",				// Disabling GMS in the provision will cause GMS in owner user being killed too due to its single user nature, causing weird ANR.
			"com.google.android.feedback",			// Used by GMS for crash report.
			"com.google.android.webview",			// WebView provider (Google)
			// Enabled system apps with launcher activity by default
			"com.android.vending",					// Google Play Store to let user install apps directly within
			"com.android.contacts",					// Contacts
			"com.android.providers.downloads.ui",	// Downloads
			// MIUI-specific
			"com.miui.core"							// Required by com.lbe.security.miui (Runtime permission UI of MIUI)
	);
	private static final Collection<Intent> sCriticalActivityIntents = Arrays.asList(
			new Intent(Intent.ACTION_INSTALL_PACKAGE),				// Usually com.android.packageinstaller, may be altered by ROM.
			new Intent(Settings.ACTION_SETTINGS),					// Usually com.android.settings
			new Intent("android.content.pm.action.REQUEST_PERMISSIONS"),	// Hidden PackageManager.ACTION_REQUEST_PERMISSIONS
			// Runtime permission UI, may be special system app on some ROMs (e.g. MIUI)
			new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),	// Usually com.android.documentsui, may be file explorer app on some ROMs. (e.g. MIUI)
			new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("*/*"),
			new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI),	// Contact picker, usually com.android.contacts
			new Intent(Intent.ACTION_VIEW, Uri.parse("http://g.cn"))// Web browser (required by some apps to open web link)
	);
	private static final Collection<String> sCriticalContentAuthorities = Arrays.asList(
			ContactsContract.AUTHORITY,				// Usually com.android.providers.contacts
			CalendarContract.AUTHORITY,				// Usually com.android.providers.calendar
			CallLog.AUTHORITY,						// Usually com.android.providers.contacts (originally com.android.providers.calllogbackup)
			Telephony.Carriers.CONTENT_URI.getAuthority(),	// Usually com.android.providers.telephony
			MediaStore.AUTHORITY,					// Usually com.android.providers.media
			"downloads",							// Usually com.android.providers.downloads
			"com.android.externalstorage.documents" // Usually com.android.externalstorage
	);

	public void prepareSystemApps() {
		disableNonCriticalSystemApps();
		enableRequiredSystemApps();
	}

	/** This is generally not necessary on AOSP but will eliminate various problems on custom ROMs */
	private void disableNonCriticalSystemApps() {
		final PackageManager pm = mContext.getPackageManager();
		final Set<String> critical_sys_pkgs = new HashSet<>(sCriticalSystemPkgs);
		// Detect package names for critical intent actions, as an addition to the whitelist of well-known ones.
		for (final Intent intent : sCriticalActivityIntents) {
			mIslandManager.enableSystemAppForActivity(intent);
			final List<ResolveInfo> activities = pm.queryIntentActivities(intent, MATCH_DEFAULT_ONLY);
			FluentIterable.from(activities).filter(info -> (info.activityInfo.applicationInfo.flags & FLAG_SYSTEM) != 0)
					.transform(info -> info.activityInfo.packageName)
					.filter(pkg -> { Log.i(TAG, "Critical package for " + intent + ": " + pkg); return true; })
					.copyInto(critical_sys_pkgs);
		}
		// Detect package names for critical content providers, as an addition to the whitelist of well-known ones.
		for (final String authority : sCriticalContentAuthorities) {
			final ProviderInfo provider = pm.resolveContentProvider(authority, 0);
			if (provider == null || (provider.applicationInfo.flags & FLAG_SYSTEM) == 0 || (provider.flags & FLAG_SINGLE_USER) != 0) continue;
			Log.i(TAG, "Critical package for authority \"" + authority + "\": " + provider.packageName);
			critical_sys_pkgs.add(provider.packageName);
		}

		final List<ApplicationInfo> apps = pm.getInstalledApplications(0);
		final ImmutableList<ApplicationInfo> sys_apps_to_hide = FluentIterable.from(apps)
				.filter(SystemAppsManager::isSystemApp)
				.filter(info -> ! critical_sys_pkgs.contains(info.packageName))
				.toList();
		for (final ApplicationInfo app : sys_apps_to_hide) {
			if (hasSingleUserComponent(pm, app.packageName)) {
				Log.i(TAG, "Not disabling system app capable for multi-user: " + app.packageName);
			} else {
				Log.i(TAG, "Disable non-critical system app: " + app.packageName);
				mIslandManager.freezeApp(app.packageName);
			}
		}
	}

	/** Enable system apps responsible for required intents. (package name unspecified) */
	private void enableRequiredSystemApps() {
		mIslandManager.enableSystemAppForActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE));
	}

	private boolean hasSingleUserComponent(final PackageManager pm, final String pkg) {
		try {
			final PackageInfo info = pm.getPackageInfo(pkg, GET_SERVICES | GET_PROVIDERS);
			if (info.services != null) for (final ServiceInfo service : info.services)
				if ((service.flags & ServiceInfo.FLAG_SINGLE_USER) != 0) return true;
			if (info.providers != null) for (final ProviderInfo service : info.providers)
				if ((service.flags & ProviderInfo.FLAG_SINGLE_USER) != 0) return true;
			return false;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	private static boolean isSystemApp(final ApplicationInfo info) { return (info.flags & FLAG_SYSTEM) != 0; }

	public SystemAppsManager(final Context context, final IslandManager island) {
		mContext = context;
		mIslandManager = island;
	}

	private final Context mContext;
	private final IslandManager mIslandManager;
	private static final String TAG = "Island.SysApps";
}
