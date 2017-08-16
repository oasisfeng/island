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

package com.android.packageinstaller.permission.model;

import com.oasisfeng.island.permission.mirror.PackageManager;

public final class Permission {
    private final String mName;
    private final String mAppOp;

    private boolean mGranted;
    private boolean mAppOpAllowed;
    private int mFlags;

    public Permission(String name, boolean granted,
            String appOp, boolean appOpAllowed, int flags) {
        mName = name;
        mGranted = granted;
        mAppOp = appOp;
        mAppOpAllowed = appOpAllowed;
        mFlags = flags;
    }

    public String getName() {
        return mName;
    }

    public String getAppOp() {
        return mAppOp;
    }

    public int getFlags() {
        return mFlags;
    }

    public boolean hasAppOp() {
        return mAppOp != null;
    }

    public boolean isGranted() {
        return mGranted;
    }

    public boolean isReviewRequired() {
        return (mFlags & PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED) != 0;
    }

    public void resetReviewRequired() {
        mFlags &= ~PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
    }

    public void setGranted(boolean mGranted) {
        this.mGranted = mGranted;
    }

    public boolean isAppOpAllowed() {
        return mAppOpAllowed;
    }

    public boolean isUserFixed() {
        return (mFlags & PackageManager.FLAG_PERMISSION_USER_FIXED) != 0;
    }

    public void setUserFixed(boolean userFixed) {
        if (userFixed) {
            mFlags |= PackageManager.FLAG_PERMISSION_USER_FIXED;
        } else {
            mFlags &= ~PackageManager.FLAG_PERMISSION_USER_FIXED;
        }
    }

    public boolean isSystemFixed() {
        return (mFlags & PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0;
    }

    public boolean isPolicyFixed() {
        return (mFlags & PackageManager.FLAG_PERMISSION_POLICY_FIXED) != 0;
    }

    public boolean isUserSet() {
        return (mFlags & PackageManager.FLAG_PERMISSION_USER_SET) != 0;
    }

    public boolean isGrantedByDefault() {
        return (mFlags & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0;
    }

    public void setUserSet(boolean userSet) {
        if (userSet) {
            mFlags |= PackageManager.FLAG_PERMISSION_USER_SET;
        } else {
            mFlags &= ~PackageManager.FLAG_PERMISSION_USER_SET;
        }
    }

    public void setPolicyFixed(boolean policyFixed) {
        if (policyFixed) {
            mFlags |= PackageManager.FLAG_PERMISSION_POLICY_FIXED;
        } else {
            mFlags &= ~PackageManager.FLAG_PERMISSION_POLICY_FIXED;
        }
    }

    public boolean shouldRevokeOnUpgrade() {
        return (mFlags & PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0;
    }

    public void setRevokeOnUpgrade(boolean revokeOnUpgrade) {
        if (revokeOnUpgrade) {
            mFlags |= PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
        } else {
            mFlags &= ~PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
        }
    }

    public void setAppOpAllowed(boolean mAppOpAllowed) {
        this.mAppOpAllowed = mAppOpAllowed;
    }
}