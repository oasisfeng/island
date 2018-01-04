/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.island.provisioning.task;

import android.content.pm.ApplicationInfo;
import android.os.RemoteException;

import com.oasisfeng.island.provisioning.task.DeleteNonRequiredAppsTask.IPackageManager;
import com.oasisfeng.island.util.Hacks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class containing various auxiliary methods.
 */
class Utils {

    public Utils() {}

    /**
     * Returns the currently installed system apps on a given user.
     *
     * <p>Calls into the {@link IPackageManager} to retrieve all installed packages on the given
     * user and returns the package names of all system apps.
     *
     * @param ipm an {@link IPackageManager} object
     * @param userId the id of the user we are interested in
     */
    public Set<String> getCurrentSystemApps(IPackageManager ipm, int userId) {
        Set<String> apps = new HashSet<String>();
        List<ApplicationInfo> aInfos = null;
        try {
            aInfos = ipm.getInstalledApplications(
                    Hacks.MATCH_ANY_USER_AND_UNINSTALLED, userId).getList();
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        for (ApplicationInfo aInfo : aInfos) {
            if ((aInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                apps.add(aInfo.packageName);
            }
        }
        return apps;
    }
}
