Delegation API
================

Island provide an API mechanism similar to the "Delegated Scopes" introduced in Android 8, but backward-compatible back to 5.x.

Authorization
---------------

First of all, required delegation(s) must be declared in `AndroidManifest.xml` as meta-data: (separated by comma)

`<meta-data android:name="com.oasisfeng.island.delegation" android:value="delegation-package-access,-island-delegation-app-ops" />`

Before invoking any of the privileged APIs, you can check and request authorization with `RestrictionsManager`:

```
    final String TYPE_DELEGATION = "com.oasisfeng.island.delegation";
    final String DELEGATION_APP_OPS = "-island-delegation-app-ops";

    final RestrictionsManager rm = (RestrictionsManager) context.getSystemService(RESTRICTIONS_SERVICE);
    if (rm.hasRestrictionsProvider()) { // Current user is not managed by Island or Island version is too low if no restrictions provider
        final Bundle restrictions = ((UserManager) context.getSystemService(Context.USER_SERVICE)).getApplicationRestrictions(context.getPackageName());
        final String[] delegations = restrictions.getStringArray(TYPE_DELEGATION);
        if (delegations == null || ! Arrays.asList(delegations).contains(DELEGATION_APP_OPS)) {
            final PersistableBundle request = new PersistableBundle();
            request.putString(RestrictionsManager.REQUEST_KEY_DATA, DELEGATION_APP_OPS);
            ((RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE)).requestPermission(TYPE_DELEGATION, "", request)
        }
    }
```

Invocation
------------

If authorized, you can bind to this service of Island to get the internal binder of delegated system service.

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
