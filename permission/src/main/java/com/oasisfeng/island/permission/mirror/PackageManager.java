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

package com.oasisfeng.island.permission.mirror;

/** Mirror hidden constants in PackageManager. */
public class PackageManager {

	/**
	 * The action used to request that the user approve a permission request
	 * from the application.
	 *
	 * @hide
	 */
	public static final String ACTION_REQUEST_PERMISSIONS =
			"android.content.pm.action.REQUEST_PERMISSIONS";

	/**
	 * The names of the requested permissions.
	 * <p>
	 * <strong>Type:</strong> String[]
	 * </p>
	 *
	 * @hide
	 */
	public static final String EXTRA_REQUEST_PERMISSIONS_NAMES =
			"android.content.pm.extra.REQUEST_PERMISSIONS_NAMES";

	/**
	 * The results from the permissions request.
	 * <p>
	 * <strong>Type:</strong> int[] of #PermissionResult
	 * </p>
	 *
	 * @hide
	 */
	public static final String EXTRA_REQUEST_PERMISSIONS_RESULTS
			= "android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS";

	/**
	 * Permission flag: The permission is set in its current state
	 * by the user and apps can still request it at runtime.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_USER_SET = 1 << 0;

	/**
	 * Permission flag: The permission is set in its current state
	 * by the user and it is fixed, i.e. apps can no longer request
	 * this permission.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_USER_FIXED =  1 << 1;

	/**
	 * Permission flag: The permission is set in its current state
	 * by device policy and neither apps nor the user can change
	 * its state.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_POLICY_FIXED =  1 << 2;

	/**
	 * Permission flag: The permission is set in a granted state but
	 * access to resources it guards is restricted by other means to
	 * enable revoking a permission on legacy apps that do not support
	 * runtime permissions. If this permission is upgraded to runtime
	 * because the app was updated to support runtime permissions, the
	 * the permission will be revoked in the upgrade process.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_REVOKE_ON_UPGRADE =  1 << 3;

	/**
	 * Permission flag: The permission is set in its current state
	 * because the app is a component that is a part of the system.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_SYSTEM_FIXED =  1 << 4;

	/**
	 * Permission flag: The permission is granted by default because it
	 * enables app functionality that is expected to work out-of-the-box
	 * for providing a smooth user experience. For example, the phone app
	 * is expected to have the phone permission.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_GRANTED_BY_DEFAULT =  1 << 5;

	/**
	 * Permission flag: The permission has to be reviewed before any of
	 * the app components can run.
	 *
	 * @hide
	 */
	public static final int FLAG_PERMISSION_REVIEW_REQUIRED =  1 << 6;

	public static final int GET_PERMISSIONS = android.content.pm.PackageManager.GET_PERMISSIONS;
	public static final int PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED;
	public static final int PERMISSION_DENIED = android.content.pm.PackageManager.PERMISSION_DENIED;
}
