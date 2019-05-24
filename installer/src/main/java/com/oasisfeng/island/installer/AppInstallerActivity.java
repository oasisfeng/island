package com.oasisfeng.island.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.android.widget.Toasts;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.appops.AppOpsCompat;
import com.oasisfeng.island.util.CallerAwareActivity;
import com.oasisfeng.island.util.Hacks;
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
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java9.util.Optional;

import static android.Manifest.permission.MANAGE_DOCUMENTS;
import static android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES;
import static com.oasisfeng.island.analytics.Analytics.Param.CONTENT;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.LOCATION;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

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
	private static final String EXTRA_INSTALL_RESULT = "android.intent.extra.INSTALL_RESULT";		// Intent.EXTRA_INSTALL_RESULT
	private static final int INSTALL_SUCCEEDED = 1;													// PackageManager.INSTALL_SUCCEEDED
	private static final int INSTALL_FAILED_INTERNAL_ERROR = -110;									// PackageManager.INSTALL_FAILED_INTERNAL_ERROR
	private static final String EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS";		// PackageInstall.EXTRA_LEGACY_STATUS

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (! prepare()) finish();
	}

	private boolean prepare() {
		final Uri data = getIntent().getData();
		if (data == null) return false;
		mCallerPackage = getCallingPackage();
		if (mCallerPackage == null) {
			Log.w(TAG, "Caller is unknown, fallback to default package installer.");
			fallbackToSystemPackageInstaller("unknown_caller", null);
			return true;
		}
		mCallerAppInfo = Apps.of(this).getAppInfo(mCallerPackage);	// Null if caller is not in the same user and has no launcher activity
		if (SDK_INT >= O && mCallerAppInfo != null && ! isCallerQualified(mCallerAppInfo)) return false;

		if (ContentResolver.SCHEME_FILE.equals(data.getScheme())) {
			final String path = data.getPath();
			if (path == null) return false;
			final File file = new File(path);
			if (! file.exists()) return false;
		}

		if (! mCallerPackage.equals(getPackageName()) && ! requireNonNull(PreferenceManager.getDefaultSharedPreferences(this)
				.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet())).contains(mCallerPackage)) {
			final CharSequence label = parseApkLabel(data);
			final String message = getString(SCHEME_PACKAGE.equals(data.getScheme()) ? R.string.dialog_clone_confirmation
					: R.string.dialog_install_confirmation, mCallerAppLabel.get(),
					label != null ? label : getString(R.string.label_unknown_app));
			final Dialogs.Builder dialog = Dialogs.buildAlert(this, null, message);
			final View view = View.inflate(dialog.getContext()/* For consistent styling */, R.layout.dialog_checkbox, null);
			final CheckBox checkbox = view.findViewById(R.id.checkbox);
			checkbox.setText(getString(R.string.dialog_install_checkbox_always_allow));
			dialog.withCancelButton().withOkButton(() -> {
				if (checkbox.isChecked()) addAlwaysAllowedCallerPackage(mCallerPackage);
				performInstall(data);
			}).setOnDismissListener(d -> finish()).setView(view).setCancelable(false).show();
		} else performInstall(data);		// Whitelisted caller to perform installation without confirmation
		return true;
	}

	private @Nullable String parseApkLabel(final Uri uri) {
		ParcelFileDescriptor fd = null;
		try {
			final String path;
			if (! ContentResolver.SCHEME_FILE.equals(uri.getScheme())) try {
				fd = getContentResolver().openFileDescriptor(uri, "r");
				if (fd == null) return null;
				path = "/proc/self/fd/" + fd.getFd();
			} catch (final IOException e) {
				Log.e(TAG, "Error opening " + uri);
				return null;
			} else path = uri.getPath();
			final PackageInfo pkg_info = getPackageManager().getPackageArchiveInfo(path, 0);	// Special path for open file descriptor
			if (pkg_info == null) return null;
			final ApplicationInfo app_info = pkg_info.applicationInfo;
			final CharSequence app_label;
			if (app_info.nonLocalizedLabel == null && Hacks.AssetManager_constructor != null && Hacks.AssetManager_addAssetPath != null) {
				final AssetManager am = Hacks.AssetManager_constructor.invoke().statically();
				Hacks.AssetManager_addAssetPath.invoke(path).on(am);
				app_label = new Resources(am, null, null).getText(app_info.labelRes);
			} else app_label = app_info.nonLocalizedLabel;
			return app_label == null ? app_info.packageName : app_label.toString() + " (" + app_info.packageName + ")";
		} finally {
			IoUtils.closeQuietly(fd);
		}
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		try { unregisterReceiver(mStatusCallback); } catch (final RuntimeException ignored) {}
		if (mProgressDialog != null) mProgressDialog.dismiss();
		if (mSession != null) mSession.abandon();
	}

	private void addAlwaysAllowedCallerPackage(final String pkg) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final Set<String> pkgs = new HashSet<>(preferences.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet()));
		pkgs.add(pkg);
		preferences.edit().putStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, pkgs).apply();
	}

	private void performInstall(final Uri uri) {
		if (SDK_INT >= P && ! getPackageManager().canRequestPackageInstalls()) try {
			new AppOpsCompat(this).setMode(AppOpsCompat.OP_REQUEST_INSTALL_PACKAGES, Process.myUid(), getPackageName(), MODE_ALLOWED);
		} catch (final RuntimeException e) {
			Analytics.$().logAndReport(TAG, "Error granting permission REQUEST_INSTALL_PACKAGES", e);
		}

		final Map<String, InputStream> input_streams = new LinkedHashMap<>();
		final String base_name = "Island[" + mCallerPackage + "]";
		try {
			if (SCHEME_PACKAGE.equals(uri.getScheme())) {
				final String pkg = uri.getSchemeSpecificPart();
				ApplicationInfo info = Apps.of(this).getAppInfo(pkg);
				if (info == null && (info = getIntent().getParcelableExtra(InstallerExtras.EXTRA_APP_INFO)) == null) {
					Log.e(TAG, "Cannot read app info of " + pkg);
					finish();	// Do not fall-back to default package installer, since it will fail too.
					return;
				}
				// TODO: Reject forward-locked package
				input_streams.put(base_name, new FileInputStream(info.publicSourceDir));
				if (info.splitPublicSourceDirs != null)
					for (int i = 0, num_splits = info.splitPublicSourceDirs.length; i < num_splits; i ++) {
						final String split = info.splitPublicSourceDirs[i];
						input_streams.put(SDK_INT >= O ? info.splitNames[i] : "split" + i, new FileInputStream(split));
					}
			} else input_streams.put(base_name, requireNonNull(getContentResolver().openInputStream(uri)));
		} catch(final IOException | RuntimeException e) {		// SecurityException may be thrown by ContentResolver.openInputStream().
			Log.w(TAG, "Error opening " + uri + " for reading.\nTo launch Island app installer, " +
					"please ensure data URI is accessible by Island, either exposed by content provider or world-readable (on pre-N)", e);
			fallbackToSystemPackageInstaller("stream_error", e);	// Default system package installer may have privilege to access the content that we can't.
			for (final Map.Entry<String, InputStream> entry : input_streams.entrySet()) IoUtils.closeQuietly(entry.getValue());
			return;
		}

		final PackageInstaller installer = getPackageManager().getPackageInstaller();
		final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
		final int session_id;
		try {
			mSession = installer.openSession(session_id = installer.createSession(params));
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

		final boolean should_return_result = getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false);
		mStatusCallback = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
			if (BuildConfig.DEBUG) Log.i(TAG, "Status received: " + intent.toUri(Intent.URI_INTENT_SCHEME));
			switch (status) {
			case PackageInstaller.STATUS_SUCCESS:
				unregisterReceiver(this);
				if (should_return_result)		// Implement the exact same result data as InstallSuccess in PackageInstaller
					AppInstallerActivity.this.setResult(Activity.RESULT_OK, new Intent().putExtra(EXTRA_INSTALL_RESULT, INSTALL_SUCCEEDED));
				AppInstallationNotifier.onPackageInstalled(context, mCallerPackage, mCallerAppLabel.get(), intent.getStringExtra(EXTRA_PACKAGE_NAME));
				finish();
				break;
			case PackageInstaller.STATUS_PENDING_USER_ACTION:
				final Intent action = intent.getParcelableExtra(Intent.EXTRA_INTENT);
				if (action != null) try {
					startActivity(action.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT));
				} catch (final ActivityNotFoundException e) {
					fallbackToSystemPackageInstaller("ActivityNotFoundException:PENDING_USER_ACTION", e);
				} else finish();    // Should never happen
				break;
			case PackageInstaller.STATUS_FAILURE_ABORTED:		// Aborted by user or us, no explicit feedback needed.
				if (should_return_result) AppInstallerActivity.this.setResult(Activity.RESULT_CANCELED);
				finish();
				break;
			default:
				if (! should_return_result) {
					String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
					if (message == null) message = getString(R.string.dialog_install_unknown_failure_message);
					Analytics.$().event("installer_failure").with(LOCATION, uri.toString()).with(CONTENT, message).send();
					if (isFinishing()) fallbackToSystemPackageInstaller("alternative.auto", null);
					else Dialogs.buildAlert(AppInstallerActivity.this, getString(R.string.dialog_install_failure_title), message).withOkButton(() -> finish())
							.setNeutralButton(R.string.fallback_to_sys_installer, (d, w) -> fallbackToSystemPackageInstaller("alternate", null))
							.setOnDismissListener(d -> finish()).show();
				} else AppInstallerActivity.this.setResult(Activity.RESULT_FIRST_USER,	// The exact same result data as InstallFailed in PackageInstaller
						new Intent().putExtra(EXTRA_INSTALL_RESULT, getIntent().getIntExtra(EXTRA_LEGACY_STATUS, INSTALL_FAILED_INTERNAL_ERROR)));
			}
		}};
		final Intent callback = new Intent("INSTALL_STATUS").setPackage(getPackageName());
		registerReceiver(mStatusCallback, new IntentFilter(callback.getAction()));

		mSession.commit(PendingIntent.getBroadcast(this, session_id, callback, FLAG_UPDATE_CURRENT).getIntentSender());

		mProgressDialog = Dialogs.buildProgress(this, getString(R.string.progress_dialog_installing, mCallerAppLabel.get()))
				.indeterminate().nonCancelable().start();
	}

	private void fallbackToSystemPackageInstaller(final String reason, final @Nullable Exception e) {
		final Intent intent = new Intent(getIntent()).setPackage(null).setComponent(null);
		Analytics.$().event("installer_fallback").with(LOCATION, intent.getDataString()).with(ITEM_CATEGORY, reason).with(CONTENT, e != null ? e.toString() : null).send();
		for (final String category : Optional.ofNullable(intent.getCategories()).orElse(Collections.emptySet())) intent.removeCategory(category);

		if (SDK_INT >= O && ! Users.isOwner() && SCHEME_PACKAGE.equals(intent.getScheme())) {	// Scheme "package" is no go in managed profile since Android O.
			final String pkg = requireNonNull(intent.getData()).getSchemeSpecificPart();
			ApplicationInfo info = Apps.of(this).getAppInfo(pkg);
			if (info == null) info = intent.getParcelableExtra(InstallerExtras.EXTRA_APP_INFO);
			if (info != null && (info.splitPublicSourceDirs == null || info.splitPublicSourceDirs.length == 0))
				intent.setData(Uri.fromFile(new File(info.publicSourceDir)));
		}

		final int flags = PackageManager.MATCH_DEFAULT_ONLY | (SDK_INT >= N ? PackageManager.MATCH_SYSTEM_ONLY : 0);
		final List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(intent, flags);
		for (final ResolveInfo candidate : candidates) {
			final ActivityInfo activity = candidate.activityInfo;
			if ((activity.applicationInfo.flags & FLAG_SYSTEM) == 0) continue;
			final ComponentName component = new ComponentName(activity.packageName, activity.name);
			intent.setComponent(component).setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
			Log.i(TAG, "Redirect to system package installer: " + component.flattenToShortString());

			final PackageManager pm = getPackageManager();
			final StrictMode.VmPolicy vm_policy = StrictMode.getVmPolicy();
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());		// Workaround to suppress FileUriExposedException.
			try {
				final Intent uas_settings;
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

	private String mCallerPackage;
	private @Nullable ApplicationInfo mCallerAppInfo;
	private final Supplier<CharSequence> mCallerAppLabel = Suppliers.memoizeWithExpiration(() ->	// Long cache time to workaround temporarily unavailable-
			mCallerAppInfo != null ? Apps.of(this).getAppName(mCallerAppInfo) : mCallerPackage, 30, SECONDS);	// label of self-updated app
	private PackageInstaller.Session mSession;
	private BroadcastReceiver mStatusCallback;
	private ProgressDialog mProgressDialog;

	private static final String TAG = "Island.AIA";
}
