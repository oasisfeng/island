/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.permission.ui;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.ui.handheld.GrantPermissionsViewHandlerImpl;
import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.oasisfeng.island.permission.R;
import com.oasisfeng.island.permission.mirror.PackageManager;
import com.oasisfeng.island.permission.ui.OverlayTouchActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.M;

@RequiresApi(M) @SuppressLint("Registered")
public class GrantPermissionsActivity extends OverlayTouchActivity
        implements GrantPermissionsViewHandler.ResultListener {

    protected String[] getSupportedPermissions() { return null; }   // To be overridden

    private static final String LOG_TAG = "GrantPermissionActivity";

    private String[] mRequestedPermissions;
    private int[] mGrantResults;

    private LinkedHashMap<String, GroupState> mRequestGrantPermissionGroups = new LinkedHashMap<>();

    private GrantPermissionsViewHandler mViewHandler;
    private AppPermissions mAppPermissions;

    boolean mResultSet;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_request_title);

        mViewHandler = new GrantPermissionsViewHandlerImpl(this).setResultListener(this);

        mRequestedPermissions = getIntent().getStringArrayExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);
        if (mRequestedPermissions == null) {
            mRequestedPermissions = new String[0];
        }

        final int requestedPermCount = mRequestedPermissions.length;
        mGrantResults = new int[requestedPermCount];

        if (requestedPermCount == 0) {
            setResultAndFinish();
            return;
        }

        PackageInfo callingPackageInfo = getCallingPackageInfo();

        if (callingPackageInfo == null || callingPackageInfo.requestedPermissions == null
                || callingPackageInfo.requestedPermissions.length <= 0) {
            setResultAndFinish();
            return;
        }

        // Don't allow legacy apps to request runtime permissions.
        if (callingPackageInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
            // Returning empty arrays means a cancellation.
            mRequestedPermissions = new String[0];
            mGrantResults = new int[0];
            setResultAndFinish();
            return;
        }

        DevicePolicyManager devicePolicyManager = getSystemService(DevicePolicyManager.class);
        final int permissionPolicy = devicePolicyManager.getPermissionPolicy(null);

        // If calling package is null we default to deny all.
        updateDefaultResults(callingPackageInfo, permissionPolicy);

        mAppPermissions = new AppPermissions(this, callingPackageInfo, getSupportedPermissions(), false,
                new Runnable() {
                    @Override
                    public void run() {
                        setResultAndFinish();
                    }
                });

        for (String requestedPermission : mRequestedPermissions) {
            AppPermissionGroup group = null;
            for (AppPermissionGroup nextGroup : mAppPermissions.getPermissionGroups()) {
                if (nextGroup.hasPermission(requestedPermission)) {
                    group = nextGroup;
                    break;
                }
            }
            if (group == null) {
                continue;
            }
            // We allow the user to choose only non-fixed permissions. A permission
            // is fixed either by device policy or the user denying with prejudice.
            if (!group.isUserFixed() && !group.isPolicyFixed()) {
                switch (permissionPolicy) {
                    case DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT: {
                        if (!group.areRuntimePermissionsGranted()) {
                            group.grantRuntimePermissions(false);
                        }
//                        group.setPolicyFixed();
                    } break;

                    case DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY: {
                        if (group.areRuntimePermissionsGranted()) {
                            group.revokeRuntimePermissions(false);
                        }
//                        group.setPolicyFixed();
                    } break;

                    default: {
                        if (!group.areRuntimePermissionsGranted()) {
                            mRequestGrantPermissionGroups.put(group.getName(),
                                    new GroupState(group));
                        } else {
                            group.grantRuntimePermissions(false);
                            updateGrantResults(group);
                        }
                    } break;
                }
            } else {
                // if the permission is fixed, ensure that we return the right request result
                updateGrantResults(group);
            }
        }

        setContentView(mViewHandler.createView());

        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mViewHandler.updateWindowAttributes(layoutParams);
        window.setAttributes(layoutParams);

        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // We need to relayout the window as dialog width may be
        // different in landscape vs portrait which affect the min
        // window height needed to show all content. We have to
        // re-add the window to force it to be resized if needed.
        View decor = getWindow().getDecorView();
        getWindowManager().removeViewImmediate(decor);
        getWindowManager().addView(decor, decor.getLayoutParams());
        if (mViewHandler instanceof GrantPermissionsViewHandlerImpl) {
            ((GrantPermissionsViewHandlerImpl) mViewHandler).onConfigurationChanged();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View rootView = getWindow().getDecorView();
        if (rootView.getTop() != 0) {
            // We are animating the top view, need to compensate for that in motion events.
            ev.setLocation(ev.getX(), ev.getY() - rootView.getTop());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mViewHandler.saveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mViewHandler.loadInstanceState(savedInstanceState);
    }

    private boolean showNextPermissionGroupGrantRequest() {
        final int groupCount = mRequestGrantPermissionGroups.size();

        int currentIndex = 0;
        for (GroupState groupState : mRequestGrantPermissionGroups.values()) {
            if (groupState.mState == GroupState.STATE_UNKNOWN) {
                CharSequence appLabel = mAppPermissions.getAppLabel();
                Spanned message = Html.fromHtml(getString(R.string.permission_warning_template,
                        appLabel, groupState.mGroup.getShortActionDescription()));  // Description is the short action only for permission group, but not for permission.
                // Set the permission message as the title so it can be announced.
                setTitle(message);
/*
                // Set the new grant view
                // TODO: Use a real message for the action. We need group action APIs
                Resources resources;
                try {
                    resources = getPackageManager().getResourcesForApplication(
                            groupState.mGroup.getIconPkg());
                } catch (NameNotFoundException e) {
                    // Fallback to system.
                    resources = Resources.getSystem();
                }
*/
                int icon = groupState.mGroup.getIconResId();

                mViewHandler.updateUi(groupState.mGroup.getName(), groupCount, currentIndex,
                        Icon.createWithResource(groupState.mGroup.getIconPkg(), icon), message,
                        groupState.mGroup.isUserSet());
                return true;
            }

            currentIndex++;
        }

        return false;
    }

    @Override
    public void onPermissionGrantResult(String name, boolean granted, boolean doNotAskAgain) {
        GroupState groupState = mRequestGrantPermissionGroups.get(name);
        if (groupState.mGroup != null) {
            if (granted) {
                groupState.mGroup.grantRuntimePermissions(doNotAskAgain);
                groupState.mState = GroupState.STATE_ALLOWED;
            } else {
                groupState.mGroup.revokeRuntimePermissions(doNotAskAgain);
                groupState.mState = GroupState.STATE_DENIED;
            }
            updateGrantResults(groupState.mGroup);
        }
        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
    }

    private void updateGrantResults(AppPermissionGroup group) {
        for (Permission permission : group.getPermissions()) {
            final int index = ArrayUtils.getArrayIndex(
                    mRequestedPermissions, permission.getName());
            if (index >= 0) {
                mGrantResults[index] = permission.isGranted() ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        // We do not allow backing out.
        return keyCode == KeyEvent.KEYCODE_BACK;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)  {
        // We do not allow backing out.
        return keyCode == KeyEvent.KEYCODE_BACK;
    }

    @Override
    public void finish() {
        setResultIfNeeded(RESULT_CANCELED);
        super.finish();
    }

    private int computePermissionGrantState(PackageInfo callingPackageInfo,
            String permission, int permissionPolicy) {
        boolean permissionRequested = false;

        for (int i = 0; i < callingPackageInfo.requestedPermissions.length; i++) {
            if (permission.equals(callingPackageInfo.requestedPermissions[i])) {
                permissionRequested = true;
                if ((callingPackageInfo.requestedPermissionsFlags[i]
                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    return PERMISSION_GRANTED;
                }
                break;
            }
        }

        if (!permissionRequested) {
            return PERMISSION_DENIED;
        }

        try {
            PermissionInfo pInfo = getPackageManager().getPermissionInfo(permission, 0);
            if ((pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                    != PermissionInfo.PROTECTION_DANGEROUS) {
                return PERMISSION_DENIED;
            }
        } catch (NameNotFoundException e) {
            return PERMISSION_DENIED;
        }

        switch (permissionPolicy) {
            case DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT: {
                return PERMISSION_GRANTED;
            }
            default: {
                return PERMISSION_DENIED;
            }
        }
    }

    private PackageInfo getCallingPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getCallingPackage(),
                    PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.i(LOG_TAG, "No package: " + getCallingPackage(), e);
            return null;
        }
    }

    private void updateDefaultResults(PackageInfo callingPackageInfo, int permissionPolicy) {
        final int requestedPermCount = mRequestedPermissions.length;
        for (int i = 0; i < requestedPermCount; i++) {
            String permission = mRequestedPermissions[i];
            mGrantResults[i] = callingPackageInfo != null
                    ? computePermissionGrantState(callingPackageInfo, permission, permissionPolicy)
                    : PERMISSION_DENIED;
        }
    }

    private void setResultIfNeeded(int resultCode) {
        if (!mResultSet) {
            mResultSet = true;
            logRequestedPermissionGroups();
            Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, mRequestedPermissions);
            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, mGrantResults);
            setResult(resultCode, result);
        }
    }

    private void setResultAndFinish() {
        setResultIfNeeded(RESULT_OK);
        finish();
    }

    private void logRequestedPermissionGroups() {
        if (mRequestGrantPermissionGroups.isEmpty()) {
            return;
        }

        final int groupCount = mRequestGrantPermissionGroups.size();
        List<AppPermissionGroup> groups = new ArrayList<>(groupCount);
        for (GroupState groupState : mRequestGrantPermissionGroups.values()) {
            groups.add(groupState.mGroup);
        }

//        SafetyNetLogger.logPermissionsRequested(mAppPermissions.getPackageInfo(), groups);
    }

    private static final class GroupState {
        static final int STATE_UNKNOWN = 0;
        static final int STATE_ALLOWED = 1;
        static final int STATE_DENIED = 2;

        final AppPermissionGroup mGroup;
        int mState = STATE_UNKNOWN;

        GroupState(AppPermissionGroup group) {
            mGroup = group;
        }
    }
}
