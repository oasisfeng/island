package com.oasisfeng.island.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.java.utils.IoUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.Manifest.permission.MANAGE_DOCUMENTS;
import static android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * App installer with following capabilities:
 * <ul>
 * <li> Install APK from Mainland into Island, and vice versa.
 * <li> Install app without user consent
 * </ul>
 * Created by Oasis on 2018-11-12.
 */
public class AppInstallerActivity extends Activity {

	private static final int STREAM_BUFFER_SIZE = 65536;
	private static final String PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS = "direct_install_allowed_callers";
	private static final String EXTRA_ORIGINATING_UID = "android.intent.extra.ORIGINATING_UID";		// Intent.EXTRA_ORIGINATING_UID

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final Uri uri = intent.getData();
		if (uri == null) {
			finish();
			return;
		}
		mCallerPackage = getCallingPackage();
		if (mCallerPackage == null) {
			Log.w(TAG, "Caller is unknown, redirect to default package installer.");
			fallbackToSystemPackageInstaller();
			return;
		}
		mCallerAppInfo = Apps.of(this).getAppInfo(mCallerPackage);	// Null if caller is not in the same user and has no launcher activity
		if (SDK_INT >= O && mCallerAppInfo != null && ! isCallerQualified(mCallerAppInfo)) {
			finish();
			return;
		}

		try {
			startInstall(getContentResolver().openInputStream(uri));
		} catch (final FileNotFoundException | SecurityException e) {	// May be thrown by ContentResolver.openInputStream().
			Log.w(TAG, "Error opening " + uri + " for reading.\nTo launch Island app installer, " +
					"please ensure data URI is accessible by Island, either exposed by content provider or world-readable (on pre-N)", e);
			fallbackToSystemPackageInstaller();		// Default system package installer may have privilege to access the content that we can't.
		}
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		try { unregisterReceiver(mStatusCallback); } catch (final RuntimeException ignored) {}
		if (mProgressDialog != null) mProgressDialog.dismiss();
		if (mSession != null) mSession.abandon();
	}

	private void startInstall(final InputStream stream) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final Set<String> allowed_callers = preferences.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet());
		if (! allowed_callers.contains(mCallerPackage)) {
			final Dialogs.Builder dialog = Dialogs.buildAlert(this, null, getString(R.string.dialog_install_confirmation, mCallerAppLabel.get()));
			final View view = View.inflate(dialog.getContext()/* For consistent styling */, R.layout.dialog_checkbox, null);
			final CheckBox checkbox = view.findViewById(R.id.checkbox);
			checkbox.setText(getString(R.string.dialog_install_checkbox_always_allow));
			dialog.withCancelButton().withOkButton(() -> {
				if (checkbox.isChecked()) addAlwaysAllowedCallerPackage(mCallerPackage);
				performInstall(mCallerPackage, stream);
			}).setOnDismissListener(d -> {
				IoUtils.closeQuietly(stream);
				finish();
			}).setView(view).setCancelable(false).show();
		} else performInstall(mCallerPackage, stream);		// Whitelisted caller to perform installation without confirmation
	}

	private void addAlwaysAllowedCallerPackage(final String pkg) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final Set<String> pkgs = new HashSet<>(preferences.getStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, Collections.emptySet()));
		pkgs.add(pkg);
		preferences.edit().putStringSet(PREF_KEY_DIRECT_INSTALL_ALLOWED_CALLERS, pkgs).apply();
	}

	private void performInstall(final String caller, final InputStream input) {
		final PackageInstaller installer = getPackageManager().getPackageInstaller();
		final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
		final String name = "Island[" + caller + "]";
		try (final OutputStream out = (mSession = installer.openSession(installer.createSession(params))).openWrite(name, 0, -1)) {
			final byte[] buffer = new byte[STREAM_BUFFER_SIZE];
			int count;
			while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
			mSession.fsync(out);
		} catch (final IOException e) {
			Log.e(TAG, "Error preparing installation", e);
			finish();
			return;
		} finally {
			IoUtils.closeQuietly(input);
		}

		mStatusCallback = new BroadcastReceiver() {
			@Override public void onReceive(final Context context, final Intent intent) {
				final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
				if (BuildConfig.DEBUG) Log.i(TAG, "Status received: " + intent.toUri(Intent.URI_INTENT_SCHEME));
				switch (status) {
				case PackageInstaller.STATUS_SUCCESS:
					unregisterReceiver(this);
					AppInstallationNotifier.onPackageInstalled(context, mCallerAppLabel.get(), intent.getStringExtra(EXTRA_PACKAGE_NAME));
					finish();
					break;
				case PackageInstaller.STATUS_PENDING_USER_ACTION:
					final Intent action = intent.getParcelableExtra(Intent.EXTRA_INTENT);
					if (action == null) finish();	// Should never happen
					else startActivity(action.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT));
					break;
				case PackageInstaller.STATUS_FAILURE_ABORTED:		// Aborted by user or us, no explicit feedback needed.
					finish();
					break;
				default:
					String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
					if (message == null) message = getString(R.string.dialog_install_unknown_failure_message);
					Dialogs.buildAlert(AppInstallerActivity.this, getString(R.string.dialog_install_failure_title), message)
							.setOnDismissListener(d -> finish()).show();
				}
			}
		};
		registerReceiver(mStatusCallback, new IntentFilter("test"));

		final PendingIntent status_callback = PendingIntent.getBroadcast(this, 0,
				new Intent("test").setPackage(getPackageName()), FLAG_UPDATE_CURRENT);
		mSession.commit(status_callback.getIntentSender());

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage(getString(R.string.progress_dialog_installing, mCallerAppLabel.get()));
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		mProgressDialog.show();
	}

	private void fallbackToSystemPackageInstaller() {
		final Intent intent = new Intent(getIntent()).setPackage(null).setComponent(null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		final int flags = PackageManager.MATCH_DEFAULT_ONLY | (SDK_INT >= N ? PackageManager.MATCH_SYSTEM_ONLY : 0);
		final List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(intent, flags);
		for (final ResolveInfo candidate : candidates) {
			final ActivityInfo activity = candidate.activityInfo;
			if ((activity.applicationInfo.flags & FLAG_SYSTEM) == 0) continue;
			final ComponentName component = new ComponentName(activity.packageName, activity.name);
			Log.i(TAG, "Redirect to system package installer: " + component.flattenToShortString());

			final StrictMode.VmPolicy vm_policy = StrictMode.getVmPolicy();
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());		// Workaround to suppress FileUriExposedException.
			try {
				startActivity(intent.setComponent(component).setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT));
			} finally {
				StrictMode.setVmPolicy(vm_policy);
			}
			finish();	// No need to setResult() if default installer is started, since FLAG_ACTIVITY_FORWARD_RESULT is used.
			return;
		}
		setResult(RESULT_CANCELED);
		finish();
	}

	@RequiresApi(O) private boolean isCallerQualified(final ApplicationInfo caller_app_info) {
		if (Apps.isPrivileged(caller_app_info) && getIntent().getBooleanExtra(EXTRA_NOT_UNKNOWN_SOURCE, false))
			return true;	// From trusted source declared by privileged caller, skip checking.
		int target_api = 0;
		final int source_uid = getOriginatingUid(caller_app_info);
		if (source_uid < 0) return true;		// From trusted caller (download provider, documents UI and etc.)
		if (source_uid != caller_app_info.uid) {		// Originating source is not the caller
			final String[] pkgs = getPackageManager().getPackagesForUid(source_uid);
			if (pkgs != null) for (final String pkg : pkgs) {	// We only know its UID, use the max target API among UID-shared packages.
				final ApplicationInfo info = Apps.of(this).getAppInfo(pkg);
				if (info != null) target_api = Math.max(target_api, info.targetSdkVersion);
			}
			if (target_api == 0) {
				Log.w(TAG, "Cannot get target sdk version for UID " + source_uid);
				return false;
			}
		} else target_api = caller_app_info.targetSdkVersion;

		final String caller_pkg = caller_app_info.packageName;
		if (target_api >= O && ! declaresPermission(caller_pkg, REQUEST_INSTALL_PACKAGES)) {
			Toast.makeText(this, caller_pkg + " does not declare " + REQUEST_INSTALL_PACKAGES, Toast.LENGTH_LONG).show();
			return false;
		} else return true;
	}

	@Nullable @Override public String getCallingPackage() {
		final String caller = super.getCallingPackage();
		if (caller != null) return caller;

		if (SDK_INT >= LOLLIPOP_MR1) {
			Intent original_intent = null;
			final Intent intent = getIntent();
			if (intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)) {
				original_intent = new Intent(getIntent());
				intent.removeExtra(Intent.EXTRA_REFERRER);
				intent.removeExtra(Intent.EXTRA_REFERRER_NAME);
			}
			try {
				final Uri referrer = getReferrer();		// getReferrer() returns real calling package if no referrer extras
				if (referrer != null) return referrer.getAuthority();        // Referrer URI: android-app://<package name>
			} finally {
				if (original_intent != null) setIntent(original_intent);
			}
		} else try {	// Only for Android 5.0
			@SuppressLint("PrivateApi") final Object am = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
			@SuppressWarnings("JavaReflectionMemberAccess") final Object token = Activity.class.getMethod("getActivityToken").invoke(this);
			return (String) am.getClass().getMethod("getLaunchedFromPackage", IBinder.class).invoke(am, (IBinder) token);
		} catch (final Exception e) {
			Log.e(TAG, "Error detecting caller", e);
		}
		return null;
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

	private boolean declaresPermission(final String pkg, final String permission) {
		final PackageInfo pkg_info = Apps.of(this).getPackageInfo(pkg, GET_PERMISSIONS);
		if (pkg_info == null) return true;		// Unable to detect its declared permissions, just let it pass.
		if (pkg_info.requestedPermissions == null) return false;
		for (final String requested_permission : pkg_info.requestedPermissions)
			if (permission.equals(requested_permission)) return true;
		return false;
	}

	private String mCallerPackage;
	private @Nullable ApplicationInfo mCallerAppInfo;
	private final Supplier<CharSequence> mCallerAppLabel = Suppliers.memoizeWithExpiration(() ->
			mCallerAppInfo != null ? Apps.of(this).getAppName(mCallerAppInfo) : mCallerPackage, 3, SECONDS);
	private PackageInstaller.Session mSession;
	private BroadcastReceiver mStatusCallback;
	private ProgressDialog mProgressDialog;

	private static final String TAG = "Island.AIA";
}
