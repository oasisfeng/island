package com.oasisfeng.island.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
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

import com.oasisfeng.android.ui.Dialogs;
import com.oasisfeng.android.util.Apps;
import com.oasisfeng.island.notification.NotificationIds;
import com.oasisfeng.island.util.Hacks;
import com.oasisfeng.island.util.Permissions;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.java.utils.IoUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.android.Manifest.permission.INTERACT_ACROSS_USERS;

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

	@Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final Uri uri = intent.getData();
		if (uri == null) {
			finish();
			return;
		}
		mCallingPackage = getCallingPackage();
		if (mCallingPackage == null) {
			Log.w(TAG, "Caller is unknown, redirect to default package installer.");
			fallbackToSystemPackageInstaller();
			return;
		}
		if (SDK_INT >= O && ! isCallerQualified(mCallingPackage)) {
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
		if (! allowed_callers.contains(mCallingPackage)) {
			mCallingAppLabel = Apps.of(this).getAppName(mCallingPackage);
			final Dialogs.Builder dialog = Dialogs.buildAlert(this, null, getString(R.string.dialog_install_comfirmation, mCallingAppLabel));
			final View view = View.inflate(dialog.getContext()/* For consistent styling */, R.layout.dialog_checkbox, null);
			final CheckBox checkbox = view.findViewById(R.id.checkbox);
			checkbox.setText(getString(R.string.dialog_install_checkbox_always_allow));
			dialog.withCancelButton().withOkButton(() -> {
				if (checkbox.isChecked()) addAlwaysAllowedCallerPackage(mCallingPackage);
				performInstall(mCallingPackage, stream);
			}).setOnDismissListener(d -> {
				IoUtils.closeQuietly(stream);
				finish();
			}).setView(view).setCancelable(false).show();
		} else performInstall(mCallingPackage, stream);		// Whitelisted caller to perform installation without confirmation
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

		final CharSequence caller_app_name = mCallingAppLabel != null ? mCallingAppLabel : Apps.of(this).getAppName(caller);
		mStatusCallback = new BroadcastReceiver() {
			@Override public void onReceive(final Context context, final Intent intent) {
				final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
				if (BuildConfig.DEBUG) Log.i(TAG, "Status received: " + intent.toUri(Intent.URI_INTENT_SCHEME));
				switch (status) {
				case PackageInstaller.STATUS_SUCCESS:
					unregisterReceiver(this);
					showInstallationNotification(context, intent, caller_app_name);
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

			private void showInstallationNotification(final Context context, final Intent intent, final CharSequence caller_app_name) {
				final String pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
				ApplicationInfo app_info = null;
				if (pkg != null) try {
					app_info = getPackageManager().getApplicationInfo(pkg, GET_UNINSTALLED_PACKAGES);
				} catch (final PackageManager.NameNotFoundException ignored) {}
				if (app_info != null) NotificationIds.AppInstallation.post(context, new Notification.Builder(context)
						.setSmallIcon(R.drawable.ic_landscape_black_24dp).setColor(getResources().getColor(R.color.accent))
						.setContentTitle(getString(R.string.notification_app_installed, Apps.of(context).getAppName(pkg)))
						.setContentText(getString(R.string.notification_app_installed_by, caller_app_name)));
			}
		};
		registerReceiver(mStatusCallback, new IntentFilter("test"));

		final PendingIntent status_callback = PendingIntent.getBroadcast(this, 0,
				new Intent("test").setPackage(getPackageName()), FLAG_UPDATE_CURRENT);
		mSession.commit(status_callback.getIntentSender());

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage(getString(R.string.progress_dialog_installing, caller_app_name));
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

	@RequiresApi(O) private boolean isCallerQualified(final String caller_pkg) {
		ApplicationInfo caller_info = null;
		final boolean has_iau_permission = Permissions.has(this, INTERACT_ACROSS_USERS);
		final int flags = has_iau_permission ? Hacks.GET_ANY_USER_AND_UNINSTALLED : GET_UNINSTALLED_PACKAGES;
		try { @SuppressLint("WrongConstant") @SuppressWarnings("unused")
			final ApplicationInfo unused = caller_info = getPackageManager().getApplicationInfo(caller_pkg, flags);
		} catch (final PackageManager.NameNotFoundException e) {	// It happens in managed profile if caller is owner-user-exclusive
			if (Users.isProfile() && ! has_iau_permission) return true;	// No way to check, just let it pass as this qualification is not a big deal.
		}
		if (caller_info == null) {
			Log.e(TAG, "Requesting package not found: " + caller_pkg);
			return false;
		}
		if (caller_info.targetSdkVersion < 0) {
			Log.w(TAG, "Cannot get target sdk version for " + caller_pkg);
			return false;
		}
		if (caller_info.targetSdkVersion >= O && ! declaresPermission(caller_info.packageName, REQUEST_INSTALL_PACKAGES, flags)) {
			Log.e(TAG, "Requesting package " + caller_pkg + " needs to declare permission " + REQUEST_INSTALL_PACKAGES);
			return false;
		}
		return true;
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

	private boolean declaresPermission(final String pkg, final String permission, final int flags) {
		try {
			final PackageInfo pkg_info = getPackageManager().getPackageInfo(pkg, GET_PERMISSIONS | flags);
			if (pkg_info.requestedPermissions == null) return false;
			for (final String requested_permission : pkg_info.requestedPermissions)
				if (permission.equals(requested_permission)) return true;
		} catch (final PackageManager.NameNotFoundException ignored) {}		// Should hardly happen
		return false;
	}

	private String mCallingPackage;
	private CharSequence mCallingAppLabel;
	private PackageInstaller.Session mSession;
	private BroadcastReceiver mStatusCallback;
	private ProgressDialog mProgressDialog;

	private static final String TAG = "Island.AIA";
}
