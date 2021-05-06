package com.oasisfeng.island.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.widget.Toasts;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.appops.AppOpsCompat;
import com.oasisfeng.island.installer.analyzer.ApkAnalyzer;
import com.oasisfeng.island.util.CallerAwareActivity;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.java.utils.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import kotlin.Unit;

import static android.Manifest.permission.MANAGE_DOCUMENTS;
import static android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageInstaller.SessionParams.MODE_INHERIT_EXISTING;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES;
import static com.oasisfeng.island.analytics.Analytics.Param.CONTENT;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.LOCATION;
import static com.oasisfeng.island.installer.AppInstallInfo.Mode.CLONE;
import static com.oasisfeng.island.installer.AppInstallInfo.Mode.INHERIT;
import static com.oasisfeng.island.installer.AppInstallInfo.Mode.INSTALL;
import static com.oasisfeng.island.installer.AppInstallInfo.Mode.UPDATE;
import static java.util.Objects.requireNonNull;

/**
 * App installer with following capabilities:
 * <ul>
 * <li> Install APK from Mainland into Island, and vice versa.
 * <li> Install app without user consent
 * </ul>
 * Created by Oasis on 2018-11-12.
 */
public class AppInstallerActivity extends CallerAwareActivity {

	private static final int STREAM_BUFFER_SIZE = 65536;
	private static final String PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS = "direct_install_allowed_callers";
	private static final String SCHEME_PACKAGE = "package";
	private static final String EXTRA_ORIGINATING_UID = "android.intent.extra.ORIGINATING_UID";		// Intent.EXTRA_ORIGINATING_UID

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (! prepare()) finish();
	}

	private boolean prepare() {
		final Intent intent = getIntent();
		final Uri data = intent.getData();
		if (data == null) return false;
		final String caller = getCallingPackage();
		if (caller == null) {
			Log.w(TAG, "Caller is unknown, fallback to default package installer.");
			fallbackToSystemPackageInstaller("unknown_caller", null);
			return true;
		}
		final ApplicationInfo callerInfo = Apps.of(this).getAppInfo(caller);
		if (SDK_INT >= O && callerInfo != null && ! isCallerQualified(callerInfo)) {	// Null if caller is not in the same user and has no launcher activity
			Log.w(TAG, "Reject installation for unqualified caller: " + caller);
			return false;
		}

		final DevicePolicies policies = new DevicePolicies(this);
		final boolean silent_install = policies.isActiveDeviceOwner()
				|| SDK_INT >= P && policies.isProfileOwner() && policies.getManager().isAffiliatedUser();
		final PackageManager pm = getPackageManager();
		if (SDK_INT >= P && ! pm.canRequestPackageInstalls() && silent_install) try {
			new AppOpsCompat(this).setMode(AppOpsCompat.OP_REQUEST_INSTALL_PACKAGES, Process.myUid(), getPackageName(), MODE_ALLOWED);
		} catch (final RuntimeException e) {
			Analytics.$().logAndReport(TAG, "Error granting permission REQUEST_INSTALL_PACKAGES", e);
		}

		@SuppressLint("InlinedApi") final AppInstallInfo install = this.mInstallInfo
				= new AppInstallInfo(getApplicationContext(), caller, callerInfo != null ? callerInfo.uid : Process.INVALID_UID);
		if (SCHEME_PACKAGE.equals(data.getScheme())) {
			final String cloningAppId = data.getSchemeSpecificPart();
			install.setMode(CLONE);
			install.setAppId(cloningAppId);
			install.setAppLabel(Apps.of(this).getAppName(cloningAppId));
		} else try {   // InputStream must be opened here synchronously, otherwise "SecurityException: Permission Denial".
			final InputStream input = getContentResolver().openInputStream(data);
			if (input != null) ApkAnalyzer.analyzeAsync(this, input, info -> {
				if (info != null) {
					final String appId = info.packageName;
					if (info.splitNames != null) {
						mInstallInfo.setMode(INHERIT);
						mInstallInfo.setDetails(info.splitNames[0]);
						mSessionId.thenAccept(id -> AppInstallationNotifier.cancel(this, id));  // Cancel the notification of previous session

						performInstall(data, appId);

						return Unit.INSTANCE;
					} else try {
						final ApplicationInfo current = getPackageManager().getApplicationInfo(appId, MATCH_UNINSTALLED_PACKAGES);
						mInstallInfo.setMode((current.flags & ApplicationInfo.FLAG_INSTALLED) != 0 ? UPDATE : INSTALL);
					} catch (final PackageManager.NameNotFoundException ignored) {}

					final ApplicationInfo app = info.applicationInfo;
					mInstallInfo.setAppId(appId);
					mInstallInfo.setVersionName(info.versionName);
					mInstallInfo.setAppLabel(app.loadLabel(getPackageManager())); // loadLabel() is overridden in ApplicationInfoEx
					mInstallInfo.setTargetSdkVersion(app.targetSdkVersion);
					mInstallInfo.setRequestedLegacyExternalStorage(AppInstallerUtils.hasRequestedLegacyExternalStorage(app));
				}

				mSessionId.thenAccept(sessionId -> {
					final CharSequence details = AppInstallationNotifier.onPackageInfoReady(this, sessionId,
							mInstallInfo, Apps.of(this).getPackageInfo(install.getAppId(), MATCH_UNINSTALLED_PACKAGES));
					mInstallInfo.setDetails(details);

					AppInstallerStatusReceiver.createCallback(this, install, sessionId);  // Sync AppInstallInfo by updating PendingIntent

					try {
						getPackageManager().getPackageInstaller().updateSessionAppLabel(sessionId, mInstallInfo.getAppLabel());
					} catch (final SecurityException ignored) {}    // May throw "SecurityException: Caller has no access to session 0."
				});
				return Unit.INSTANCE;
			});
		} catch (final IOException e) { Log.w(TAG, "Error opening " + data, e); }

		if (! silent_install) {     // PackageInstaller requires confirmation, thus no need for pre-confirmation on our side.
			performInstall(data, null);
			return true;
		}

		if (ContentResolver.SCHEME_FILE.equals(data.getScheme())) {
			final String path = data.getPath();
			if (path == null) {
				Log.w(TAG, "Invalid file URI: " + data);
				return false;
			}
			final File file = new File(path);
			if (! file.exists()) {
				Log.w(TAG, "File not found: " + file);
				return false;
			}
		}

		if (caller.equals(getPackageName()) || requireNonNull(PreferenceManager.getDefaultSharedPreferences(this)
				.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet())).contains(caller)) {
			performInstall(data, null);	// Whitelisted caller to perform installation without confirmation
			return true;
		}
		final String message = getString(install.getMode() == CLONE ? R.string.confirm_cloning : install.getMode() == UPDATE
				? R.string.confirm_updating : R.string.confirm_installing, install.getCallerLabel(), install.getAppLabel());
		final Dialogs.Builder dialog = Dialogs.buildAlert(this, null, message);
		final View view = View.inflate(dialog.getContext()/* For consistent styling */, R.layout.dialog_checkbox, null);
		final CheckBox checkbox = view.findViewById(R.id.checkbox);
		checkbox.setText(getString(R.string.dialog_install_checkbox_always_allow));
		dialog.withCancelButton().withOkButton(() -> {
			if (checkbox.isChecked()) addAlwaysAllowedCallerPackage(install.getCaller());
			performInstall(data, null);
		}).setOnCancelListener(d -> finish()).setView(view).setCancelable(false).show();
		return true;
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		if (mSession != null) { mSession.abandon(); mSession.close(); }
	}

	private void addAlwaysAllowedCallerPackage(final String pkg) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final Set<String> pkgs = new HashSet<>(requireNonNull(preferences.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet())));
		pkgs.add(pkg);
		preferences.edit().putStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, pkgs).apply();
	}

	/** @param base_pkg the base package name for split APK installation, or null for full installation. */
	private void performInstall(final Uri uri, final @Nullable String base_pkg) {
		final boolean is_scheme_package = SCHEME_PACKAGE.equals(uri.getScheme());
		if (is_scheme_package && base_pkg != null) throw new IllegalArgumentException("Scheme \"package\" could never be installed as split");
		final Map<String, InputStream> input_streams = new LinkedHashMap<>();
		final String stream_name = "Island[" + mInstallInfo.getCaller() + "]";
		try {
			if (is_scheme_package) {
				final String pkg = uri.getSchemeSpecificPart();
				ApplicationInfo info = Apps.of(this).getAppInfo(pkg);
				if (info == null && (info = getIntent().getParcelableExtra(InstallerExtras.EXTRA_APP_INFO)) == null) {
					Log.e(TAG, "Cannot query app info for " + pkg);
					finish();	// Do not fall-back to default package installer, since it will fail too.
					return;
				}
				// TODO: Reject forward-locked package
				input_streams.put(stream_name, new FileInputStream(info.publicSourceDir));
				if (info.splitPublicSourceDirs != null)
					for (int i = 0, num_splits = info.splitPublicSourceDirs.length; i < num_splits; i ++) {
						final String split = info.splitPublicSourceDirs[i];
						input_streams.put(SDK_INT >= O ? info.splitNames[i] : "split" + i, new FileInputStream(split));
					}
			} else input_streams.put(stream_name, requireNonNull(getContentResolver().openInputStream(uri)));
		} catch(final IOException | RuntimeException e) {		// SecurityException may be thrown by ContentResolver.openInputStream().
			Log.w(TAG, "Error opening " + uri + " for reading.\nTo launch Island app installer, " +
					"please ensure data URI is accessible by Island, either exposed by content provider or world-readable (on pre-N)", e);
			fallbackToSystemPackageInstaller("stream_error", e);	// Default system package installer may have privilege to access the content that we can't.
			for (final Map.Entry<String, InputStream> entry : input_streams.entrySet()) IoUtils.closeQuietly(entry.getValue());
			return;
		}

		final PackageInstaller installer = getPackageManager().getPackageInstaller();
		final SessionParams params = new SessionParams(base_pkg == null ? MODE_FULL_INSTALL : MODE_INHERIT_EXISTING);
		if (mInstallInfo.getAppId() != null) params.setAppPackageName(mInstallInfo.getAppId());
		if (mInstallInfo.getAppLabel() != null) params.setAppLabel(mInstallInfo.getAppLabel());
		if (mInstallInfo.getCallerUid() != INVALID_UID) params.setOriginatingUid(mInstallInfo.getCallerUid());
		if (SDK_INT >= O) params.setInstallReason(PackageManager.INSTALL_REASON_USER);
		final int session_id;
		try {
			session_id = installer.createSession(params);
			mSession = installer.openSession(session_id);
			mSessionId.complete(session_id);
			for (final Map.Entry<String, InputStream> entry : input_streams.entrySet())
				try (final OutputStream out = mSession.openWrite(entry.getKey(), 0, - 1)) {
					final byte[] buffer = new byte[STREAM_BUFFER_SIZE];
					final InputStream input = entry.getValue();
					int count;
					while ((count = input.read(buffer)) != - 1) out.write(buffer, 0, count);
					mSession.fsync(out);
				}
		} catch (final IOException | RuntimeException e) {
			Log.e(TAG, "Error preparing installation", e);
			fallbackToSystemPackageInstaller("session", e);
			return;
		} finally {
			for (final Map.Entry<String, InputStream> entry : input_streams.entrySet()) IoUtils.closeQuietly(entry.getValue());
		}

		final PendingIntent callback = AppInstallerStatusReceiver.createCallback(this, mInstallInfo, session_id);
		mSession.commit(callback.getIntentSender());
		mSession.close();
		mSession = null;        // Otherwise it will be abandoned in onDestroy().

		AppInstallationNotifier.onInstallStart(this, session_id, mInstallInfo);
		if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) setResult(Activity.RESULT_OK);

		new Handler().postDelayed(this::finish, 500);   // A slight delay to keep in foreground, for activity launch of STATUS_PENDING_USER_ACTION.
	}

	private void fallbackToSystemPackageInstaller(final String reason, final @Nullable Exception e) {
		final Intent intent = new Intent(getIntent()).setPackage(null).setComponent(null);
		Analytics.$().event("installer_fallback").with(LOCATION, intent.getDataString()).with(ITEM_CATEGORY, reason).with(CONTENT, e != null ? e.toString() : null).send();
		for (final String category : Optional.ofNullable(intent.getCategories()).orElse(Collections.emptySet())) intent.removeCategory(category);

		if (SDK_INT >= O && ! Users.isParentProfile() && SCHEME_PACKAGE.equals(intent.getScheme())) {	// Scheme "package" is no go in managed profile since Android O.
			final String pkg = requireNonNull(intent.getData()).getSchemeSpecificPart();
			ApplicationInfo info = Apps.of(this).getAppInfo(pkg);
			if (info == null) info = intent.getParcelableExtra(InstallerExtras.EXTRA_APP_INFO);
			if (info != null) {
				intent.setData(Uri.fromFile(new File(info.publicSourceDir)));
 				if (info.splitPublicSourceDirs != null && info.splitPublicSourceDirs.length > 0)
					Toast.makeText(getApplicationContext(), R.string.toast_split_apk_clone_fallback_warning, Toast.LENGTH_LONG).show();
			}
		}

		AppInstallerUtils.ensureSystemPackageEnabledAndUnfrozen(this, intent);

		final List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY);
		for (final ResolveInfo candidate : candidates) {
			final ActivityInfo activity = candidate.activityInfo;
			if ((activity.applicationInfo.flags & FLAG_SYSTEM) == 0) continue;
			final ComponentName component = new ComponentName(activity.packageName, activity.name);
			intent.setComponent(component).setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
			Log.i(TAG, "Redirect to system package installer: " + component.flattenToShortString());

			final StrictMode.VmPolicy vm_policy = StrictMode.getVmPolicy();
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());		// Workaround to suppress FileUriExposedException.
			try {
				final Intent uas_settings; final PackageManager pm = getPackageManager();
				if (SDK_INT >= O && ! pm.canRequestPackageInstalls() && (uas_settings = new Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES,
						Uri.fromParts(SCHEME_PACKAGE, getPackageName(), null))).resolveActivity(pm) != null)
					startActivities(new Intent[] { intent, uas_settings });
				else startActivity(intent);
			} catch (final SecurityException security_exception) {		// UID {UID} does not have permission to {uri}
				fallbackToSystemPackageInstaller("uri_permission", security_exception);
				return;
			} finally {
				StrictMode.setVmPolicy(vm_policy);
			}
			finish();	// No need to setResult() if default installer is started, since FLAG_ACTIVITY_FORWARD_RESULT is used.
			return;
		}
		Log.e(TAG, "Default system package installer is missing");
		setResult(RESULT_CANCELED);
		finish();
	}

	@RequiresApi(O) private boolean isCallerQualified(final ApplicationInfo caller_app_info) {
		if (Apps.isPrivileged(caller_app_info) && getIntent().getBooleanExtra(EXTRA_NOT_UNKNOWN_SOURCE, false))
			return true;	// From trusted source declared by privileged caller, skip checking.
		final int source_uid = getOriginatingUid(caller_app_info);
		if (source_uid < 0) return true;		// From trusted caller (download provider, documents UI and etc.)
		final PackageManager pm = getPackageManager();
		final CharSequence source_label;
		if (source_uid != caller_app_info.uid) {		// Originating source is not the caller
			final String[] pkgs = pm.getPackagesForUid(source_uid);
			if (pkgs != null) for (final String pkg : pkgs)
				if (isSourceQualified(Apps.of(this).getAppInfo(pkg))) return true;
			source_label = pm.getNameForUid(source_uid);
		} else {
			if (isSourceQualified(caller_app_info)) return true;
			source_label = caller_app_info.loadLabel(pm);
		}
		Toasts.show(this, source_label + " does not declare " + REQUEST_INSTALL_PACKAGES, Toast.LENGTH_LONG);
		return false;
	}

	@RequiresApi(O) private boolean isSourceQualified(final @Nullable ApplicationInfo info) {
		return info != null && (info.targetSdkVersion < O || declaresRequestInstallPermission(info.packageName));
	}

	private int getOriginatingUid(final ApplicationInfo caller_info) {
		if (isSystemDownloadsProvider(caller_info.uid) || checkPermission(MANAGE_DOCUMENTS, 0, caller_info.uid) == PERMISSION_GRANTED)
			return getIntent().getIntExtra(EXTRA_ORIGINATING_UID, -1);	// The originating uid provided in the intent from trusted source.
		return caller_info.uid;
	}

	private boolean isSystemDownloadsProvider(final int uid) {
		final ProviderInfo provider = getPackageManager().resolveContentProvider("downloads", Apps.getFlagsMatchKnownPackages(this));
		if (provider == null) return false;
		final ApplicationInfo app_info = provider.applicationInfo;
		return (app_info.flags & FLAG_SYSTEM) != 0 && uid == app_info.uid;
	}

	private boolean declaresRequestInstallPermission(final String pkg) {
		final PackageInfo pkg_info = Apps.of(this).getPackageInfo(pkg, GET_PERMISSIONS);
		if (pkg_info == null) return true;		// Unable to detect its declared permissions, just let it pass.
		if (pkg_info.requestedPermissions == null) return false;
		for (final String requested_permission : pkg_info.requestedPermissions)
			if (REQUEST_INSTALL_PACKAGES.equals(requested_permission)) return true;
		return false;
	}

	private AppInstallInfo mInstallInfo;
	private PackageInstaller.Session mSession;
	private final CompletableFuture<Integer> mSessionId = new CompletableFuture<>();

	private static final String TAG = "Island.AIA";
}
