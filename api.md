Delegation API
================

Island provide an API mechanism similar to the "Delegated Scopes" introduced in Android 8, but backward-compatible back to 5.x.

Authorization
---------------

First of all, required delegation(s) must be declared in `AndroidManifest.xml` as meta-data: (separated by comma)

`<meta-data android:name="com.oasisfeng.island.delegation" android:value="delegation-package-access,-island-delegation-app-ops" />`

All standard delegations definded by Android SDK can be declared here, together with non-standard delegations (with vendor prefix, just like [CSS Vendor Prefix](https://developer.mozilla.org/en-US/docs/Glossary/Vendor_Prefix)). Some standard delegations requiring recent version of Android are also supported in back-port manner by Island. (see [Invocation](#invocation) section below for the instructions of back-ported delegation)

Before invoking any of the privileged APIs, you can check and request authorization with `RestrictionsManager`:

```
    final String TYPE_DELEGATION = "com.oasisfeng.island.delegation";
    final String DELEGATION_APP_OPS = "-island-delegation-app-ops";

    final RestrictionsManager rm = (RestrictionsManager) context.getSystemService(RESTRICTIONS_SERVICE);
    if (rm != null && rm.hasRestrictionsProvider()) { // Otherwise, current user is not managed by Island or the version of Island is too low.
        final Bundle restrictions = Objects.requireNonNull((UserManager) context.getSystemService(Context.USER_SERVICE)).getApplicationRestrictions(context.getPackageName());
        final String[] delegations = restrictions.getStringArray(TYPE_DELEGATION);
        if (delegations == null || ! Arrays.asList(delegations).contains(DELEGATION_APP_OPS)) {
            final PersistableBundle request = new PersistableBundle();
            request.putString(RestrictionsManager.REQUEST_KEY_DATA, DELEGATION_APP_OPS);
            rm.requestPermission(TYPE_DELEGATION, "com.example.android.app-ops", request)
        }
    }
```

Invocation
------------

For standard delegation on supported Android version, corresponding APIs can be invoked directly, as mentioned in official [Android developer documents](https://developer.android.com/work/versions/android-8.0#app-management-api-delegation).

For non-standard delegation or standard delegation on not-yet-supported Android version, you can bind to this service of Island to get the internal binder of delegated system service. The binder returned in `onServiceConnected()` needs to be injected into a system service manager (e.g. `AppOpsManager.mService`) for convenient invocation.

```
    final String ACTION_BIND_SYSTEM_SERVICE = "com.oasisfeng.island.api.action.BIND_SYSTEM_SERVICE";

    final Intent intent = new Intent(ACTION_BIND_SYSTEM_SERVICE, Uri.fromParts("service", Context.APP_OPS_SERVICE, null));
    final List<ResolveInfo> candidates = context.getPackageManager().queryIntentServices(intent, 0);
    final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    if (candidates != null) for (final ResolveInfo candidate : candidates) {
        final String pkg = candidate.serviceInfo.packageName;
        if (dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg) ) {
            if (! context.bindService(intent.setClassName(pkg, candidate.serviceInfo.name), new ServiceConnection() {
                @Override public void onServiceConnected(final ComponentName name, final IBinder binder) {
                    ...
                    context.unbindService(this);
                }

                @Override public void onServiceDisconnected(final ComponentName name) {}
            }, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Failed to bind");
            }
        }
    }
```
