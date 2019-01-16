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

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Xml;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.oasisfeng.island.engine.R;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.PackageManagerWrapper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Deletes all system apps with a launcher that are not in the required set of packages.
 * Furthermore deletes all disallowed apps.
 *
 * Note: If an app is mistakenly listed as both required and disallowed, it will be treated as
 * required.
 *
 * This task may be run when a profile (both for managed device and managed profile) is created.
 * In that case the newProfile flag should be true.
 */
@SuppressWarnings({"LocalCanBeFinal", "WeakerAccess"})
public class DeleteNonRequiredAppsTask {
    private final Callback mCallback;
    private final Context mContext;
    private final String mMdmPackageName;
    private final IPackageManager mIPackageManager;
    private final IInputMethodManager mIInputMethodManager;
    private final PackageManager mPm;
    private final List<String> mRequiredAppsList;
    private final List<String> mDisallowedAppsList;
    private final List<String> mVendorRequiredAppsList;
    private final List<String> mVendorDisallowedAppsList;
    private final int mUserId;
    private final int mProvisioningType;
    private final boolean mNewProfile; // If we are provisioning a new managed profile/device.
    private final boolean mLeaveAllSystemAppsEnabled;

    private static final String TAG_SYSTEM_APPS = "system-apps";
    private static final String TAG_PACKAGE_LIST_ITEM = "item";
    private static final String ATTR_VALUE = "value";

    public static final int DEVICE_OWNER = 0;
    public static final int PROFILE_OWNER = 1;
    public static final int MANAGED_USER = 2;

    private final Utils mUtils = new Utils();

    /**
     * Provisioning type should be either {@link #DEVICE_OWNER}, {@link #PROFILE_OWNER} or
     * {@link #MANAGED_USER}.
     **/
    public DeleteNonRequiredAppsTask(Context context, String mdmPackageName, int provisioningType,
            boolean newProfile, int userId, boolean leaveAllSystemAppsEnabled, Callback callback) {
        this(context, getIPackageManager(context), getIInputMethodManager(context), mdmPackageName,
                provisioningType, newProfile, userId, leaveAllSystemAppsEnabled, callback);
    }

    @VisibleForTesting
    DeleteNonRequiredAppsTask(Context context, IPackageManager iPm, IInputMethodManager iimm,
            String mdmPackageName, int provisioningType, boolean newProfile, int userId,
            boolean leaveAllSystemAppsEnabled, Callback callback) {

        mCallback = callback;
        mContext = context;
        mMdmPackageName = mdmPackageName;
        mProvisioningType = provisioningType;
        mUserId = userId;
        mNewProfile = newProfile;
        mLeaveAllSystemAppsEnabled = leaveAllSystemAppsEnabled;
        mPm = getPackageManager(context);
        mIPackageManager = iPm;
        mIInputMethodManager = iimm;

        int requiredAppsListArray;
        int vendorRequiredAppsListArray;
        int disallowedAppsListArray;
        int vendorDisallowedAppsListArray;
        if (mProvisioningType == DEVICE_OWNER) {
            requiredAppsListArray = R.array.required_apps_managed_device;
            disallowedAppsListArray = R.array.disallowed_apps_managed_device;
            vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_device;
            vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_device;
        } else if (mProvisioningType == PROFILE_OWNER) {
            requiredAppsListArray = R.array.required_apps_managed_profile;
            disallowedAppsListArray = R.array.disallowed_apps_managed_profile;
            vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_profile;
            vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_profile;
        } else if (mProvisioningType == MANAGED_USER) {
            requiredAppsListArray = R.array.required_apps_managed_user;
            disallowedAppsListArray = R.array.disallowed_apps_managed_user;
            vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_user;
            vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_user;
        } else {
            throw new IllegalArgumentException("Provisioning type " + mProvisioningType +
                    " not supported.");
        }

        Resources resources = getManagedProvisioningPackageResources(context);
        mRequiredAppsList = Arrays.asList(resources.getStringArray(requiredAppsListArray));
        mDisallowedAppsList = Arrays.asList(resources.getStringArray(disallowedAppsListArray));
        mVendorRequiredAppsList = Arrays.asList(
                resources.getStringArray(vendorRequiredAppsListArray));
        mVendorDisallowedAppsList = Arrays.asList(
                resources.getStringArray(vendorDisallowedAppsListArray));
    }

    public void run() {
        if (mLeaveAllSystemAppsEnabled) {
            ProvisionLogger.logd("Not deleting non-required apps.");
            mCallback.onSuccess();
            return;
        }
        ProvisionLogger.logd("Deleting non required apps.");

        Set<String> packagesToDelete = getPackagesToDelete();
        removeNonInstalledPackages(packagesToDelete);

        if (packagesToDelete.isEmpty()) {
            mCallback.onSuccess();
            return;
        }

        PackageDeleteObserver packageDeleteObserver =
                new PackageDeleteObserver(packagesToDelete.size());
        for (String packageName : packagesToDelete) {
            ProvisionLogger.logd("Deleting package [" + packageName + "] as user " + mUserId);
            mPm.deletePackageAsUser(packageName, packageDeleteObserver,
                    PackageManager.DELETE_SYSTEM_APP, mUserId);
        }
    }

    private Set<String> getPackagesToDelete() {
        Set<String> packagesToDelete = getCurrentAppsWithLauncher();
        // Newly installed system apps are uninstalled when they are not required and are either
        // disallowed or have a launcher icon.
        packagesToDelete.removeAll(getRequiredApps());
        // Don't delete the system input method packages in case of Device owner provisioning.
        if (mProvisioningType == DEVICE_OWNER || mProvisioningType == MANAGED_USER) {
            packagesToDelete.removeAll(getSystemInputMethods());
        }
        packagesToDelete.addAll(getDisallowedApps());

        // Only consider new system apps.
        packagesToDelete.retainAll(getNewSystemApps());
        return packagesToDelete;
    }

    private Set<String> getNewSystemApps() {
        File systemAppsFile = getSystemAppsFile(mContext, mUserId);
        systemAppsFile.getParentFile().mkdirs(); // Creating the folder if it does not exist

        Set<String> currentSystemApps = mUtils.getCurrentSystemApps(mIPackageManager, mUserId);
        final Set<String> previousSystemApps;
        if (mNewProfile) {
            // Provisioning case.
            previousSystemApps = Collections.<String>emptySet();
        } else  if (!systemAppsFile.exists()) {
            // OTA case.
            ProvisionLogger.loge("Could not find the system apps file " +
                    systemAppsFile.getAbsolutePath());
            mCallback.onError();
            return Collections.<String>emptySet();
        } else {
            previousSystemApps = readSystemApps(systemAppsFile);
        }

        writeSystemApps(currentSystemApps, systemAppsFile);
        Set<String> newApps = currentSystemApps;
        newApps.removeAll(previousSystemApps);
        return newApps;
    }

    /**
     * Remove all packages from the set that are not installed.
     */
    private void removeNonInstalledPackages(Set<String> packages) {
        Set<String> toBeRemoved = new HashSet<String>();
        for (String packageName : packages) {
            try {
                PackageInfo info = mPm.getPackageInfoAsUser(packageName, 0 /* default flags */,
                        mUserId);
                if (info == null) {
                    toBeRemoved.add(packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                toBeRemoved.add(packageName);
            }
        }
        packages.removeAll(toBeRemoved);
    }

    /**
     * Returns if this task should be run on OTA.
     * This is indicated by the presence of the system apps file.
     */
    public static boolean shouldDeleteNonRequiredApps(Context context, int userId) {
        return getSystemAppsFile(context, userId).exists();
    }

    static File getSystemAppsFile(Context context, int userId) {
        return new File(context.getFilesDir() + File.separator + "system_apps"
                + File.separator + "user" + userId + ".xml");
    }

    private Set<String> getCurrentAppsWithLauncher() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = mPm.queryIntentActivitiesAsUser(launcherIntent,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
                | PackageManager.MATCH_DISABLED_COMPONENTS
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                mUserId);
        Set<String> apps = new HashSet<String>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            apps.add(resolveInfo.activityInfo.packageName);
        }
        return apps;
    }

    private Set<String> getSystemInputMethods() {
        // InputMethodManager is final so it cannot be mocked.
        // So, we're using IInputMethodManager directly because it can be mocked.
        List<InputMethodInfo> inputMethods = null;
        try {
            inputMethods = mIInputMethodManager.getInputMethodList();
        } catch (RemoteException e) {
            ProvisionLogger.loge("Could not communicate with IInputMethodManager", e);
            return Collections.<String>emptySet();
        }
        Set<String> systemInputMethods = new HashSet<String>();
        for (InputMethodInfo inputMethodInfo : inputMethods) {
            ApplicationInfo applicationInfo = inputMethodInfo.getServiceInfo().applicationInfo;
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemInputMethods.add(inputMethodInfo.getPackageName());
            }
        }
        return systemInputMethods;
    }

    private void writeSystemApps(Set<String> packageNames, File systemAppsFile) {
        try {
            FileOutputStream stream = new FileOutputStream(systemAppsFile, false);
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_SYSTEM_APPS);
            for (String packageName : packageNames) {
                serializer.startTag(null, TAG_PACKAGE_LIST_ITEM);
                serializer.attribute(null, ATTR_VALUE, packageName);
                serializer.endTag(null, TAG_PACKAGE_LIST_ITEM);
            }
            serializer.endTag(null, TAG_SYSTEM_APPS);
            serializer.endDocument();
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to write the system apps", e);
        }
    }

    private Set<String> readSystemApps(File systemAppsFile) {
        Set<String> result = new HashSet<String>();
        if (!systemAppsFile.exists()) {
            return result;
        }
        try {
            FileInputStream stream = new FileInputStream(systemAppsFile);

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);

            int type = parser.next();
            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tag = parser.getName();
                if (tag.equals(TAG_PACKAGE_LIST_ITEM)) {
                    result.add(parser.getAttributeValue(null, ATTR_VALUE));
                } else {
                    ProvisionLogger.loge("Unknown tag: " + tag);
                }
            }
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to read the system apps", e);
        } catch (XmlPullParserException e) {
            ProvisionLogger.loge("XmlPullParserException trying to read the system apps", e);
        }
        return result;
    }

    protected Set<String> getRequiredApps() {
        HashSet<String> requiredApps = new HashSet<String>();
        requiredApps.addAll(mRequiredAppsList);
        requiredApps.addAll(mVendorRequiredAppsList);
        requiredApps.add(mMdmPackageName);
        return requiredApps;
    }

    private Set<String> getDisallowedApps() {
        HashSet<String> disallowedApps = new HashSet<String>();
        disallowedApps.addAll(mDisallowedAppsList);
        disallowedApps.addAll(mVendorDisallowedAppsList);
        return disallowedApps;
    }

    /**
     * Runs the next task when all packages have been deleted or shuts down the activity if package
     * deletion fails.
     */
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger mPackageCount = new AtomicInteger(0);

        public PackageDeleteObserver(int packageCount) {
            this.mPackageCount.set(packageCount);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                ProvisionLogger.logw(
                        "Could not finish the provisioning: package deletion failed");
                mCallback.onError();
                return;
            }
            int currentPackageCount = mPackageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                ProvisionLogger.logi("All non-required system apps with launcher icon, "
                        + "and all disallowed apps have been uninstalled.");
                mCallback.onSuccess();
            }
        }
    }

    private static IInputMethodManager getIInputMethodManager() {
        IBinder b = ServiceManager.getService(Context.INPUT_METHOD_SERVICE);
        return IInputMethodManager.Stub.asInterface(b);
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }

    /* ***** Code glues for strict consistency of above code ***** */

    static class ServiceManager {
        static IBinder getService(final String unused) { return null; }
    }

    private static class IPackageDeleteObserver { static class Stub { public void packageDeleted(String packageName, int returnCode) {} } }
    private static class PackageManager extends PackageManagerWrapper {

        static final int MATCH_DISABLED_COMPONENTS = android.content.pm.PackageManager.GET_DISABLED_COMPONENTS;
        static final int MATCH_DIRECT_BOOT_AWARE = SDK_INT >= N ? android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE : 0;
        static final int MATCH_DIRECT_BOOT_UNAWARE = SDK_INT >= N ? android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE : 0;
        static final int DELETE_SYSTEM_APP = 0x00000004;
        static final int DELETE_SUCCEEDED = 1;
        static final int DELETE_FAILED_INTERNAL_ERROR = -1;
        PackageManager(final Context base) { super(base.getPackageManager()); mDevicePolicies = new DevicePolicies(base); }

        void deletePackageAsUser(String pkg, PackageDeleteObserver observer, int flags, int mUserId) {
			mDevicePolicies.invoke(DevicePolicyManager::setApplicationHidden, pkg, true);
            if (mDevicePolicies.invoke(DevicePolicyManager::isApplicationHidden, pkg)) observer.packageDeleted(pkg, DELETE_SUCCEEDED);
            else observer.packageDeleted(pkg, DELETE_FAILED_INTERNAL_ERROR);
        }

        PackageInfo getPackageInfoAsUser(final String pkg, @SuppressWarnings("SameParameterValue") final int flags, final int user) throws NameNotFoundException {
			return mBase.getPackageInfo(pkg, flags);
		}

        List<ResolveInfo> queryIntentActivitiesAsUser(final Intent intent, final int flags, final int user) {
        	return mBase.queryIntentActivities(intent, flags);
        }

        private final DevicePolicies mDevicePolicies;
    }

    private static IInputMethodManager getIInputMethodManager(Context context) {
        return new IInputMethodManager((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE));
    }
    private static class IInputMethodManager {
        static class Stub { private static IInputMethodManager asInterface(final IBinder unused) { return null; } }
        private List<InputMethodInfo> getInputMethodList() throws RemoteException { return mInputMethodManager.getInputMethodList(); }
        IInputMethodManager(final InputMethodManager imm) { mInputMethodManager = imm; }
        private final InputMethodManager mInputMethodManager;
    }

    private static PackageManager getPackageManager(final Context context) {
        return new PackageManager(context);
    }

    private static IPackageManager getIPackageManager(final Context context) {
        return new IPackageManager(context.getPackageManager());
    }
    static class IPackageManager {

        interface ParceledListSlice<T> { List<T> getList(); }

        ParceledListSlice<ApplicationInfo> getInstalledApplications(final int flags, final int user) throws RemoteException {
            return () -> mPackageManager.getInstalledApplications(flags);
        }

        public IPackageManager(final android.content.pm.PackageManager pm) { mPackageManager = pm; }
        private final android.content.pm.PackageManager mPackageManager;
    }

    private static Resources getManagedProvisioningPackageResources(final Context context) {
        try {
            final Resources self_resources = context.getResources();
            final Context target_context = context.createPackageContext(getManagedProvisioningPackageName(context), 0);
            final Resources target_resources = target_context.getResources();
            return new Resources(target_resources.getAssets(), target_resources.getDisplayMetrics(), target_resources.getConfiguration()) {
                @NonNull @Override public String[] getStringArray(final int id) throws NotFoundException {
                    final String entry_name = self_resources.getResourceEntryName(id);
                    final int target_res_id = target_resources.getIdentifier(entry_name, "array", target_context.getPackageName());
                    if (target_res_id == 0) return new String[0];       // Return empty array instead of throwing NotFoundException.
                    return target_resources.getStringArray(target_res_id);
                }
            };
        } catch (final NameNotFoundException e) {
            return context.getResources();          // Fall-back to self, with default resource values.
        }
    }

    private static String getManagedProvisioningPackageName(final Context context) throws NameNotFoundException {
        @SuppressLint("WrongConstant") final List<ResolveInfo> candidates = context.getPackageManager().queryIntentActivities(
                new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE), PackageManager.GET_DISABLED_COMPONENTS
                        | PackageManager.GET_UNINSTALLED_PACKAGES | (SDK_INT >= N ? PackageManager.MATCH_SYSTEM_ONLY : 0));
        for (final ResolveInfo candidate : candidates)
            if ((candidate.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                return candidate.activityInfo.applicationInfo.packageName;
        throw new NameNotFoundException();
    }
}
