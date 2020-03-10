Setup Guide
=============

On most middle to high end Android devices released after 2016, Island can be setup straightforward without hassle. But still on some devices, you may got “incompatible with your device” message on Google Play Store, or be notified during the setup with error message “Sorry, your device (or ROM) is incompatible with Island”, or other failures. In these cases, Island could probably still work on your device if setup manually.

If you are prompted to encrypt your device during the setup, it means your device was not pre-encrypted out of box. If you don't want device decription (which may significantly degrade overall I/O performance on some low-end devices), it can be avoided with manual setup.


Preparation
-------------

First of all, you need to connect your Android device to a computer with USB cable, and the official [ADB tool](https://developer.android.com/studio/releases/platform-tools.html) provided by Google.

To check whether the USB-connected Android device is properly recognized by your computer, type the following command in the shell (or Command Prompt on Windows):

`adb devices`

If no device is listed in the output, your Android device is not correctly recognized by the computer.

For Windows PC, [this official guide and driver list for common OEM](https://developer.android.com/studio/run/oem-usb.html) might be helpful. If it does not work out, the [official Google Android USB driver](http://dl.google.com/android/repository/usb_driver_r11-windows.zip) should work for most Android devices, just manually install it, and select "Android ADB Interface" or "Android Composite ADB Interface".

**If your device is Xiaomi branded or runs MIUI**, extra steps are required:

- In system "Settings - Additional settings - Developer options", enable "USB debugging (Security settings)".
- In system "Settings - Permissions - Autostart", enable "Island". (grant auto-start permission)

Manual setup for Island
-------------------------

Type `adb -d shell` to open ADB shell, and execute the following commands one by one in sequence:

1. `pm create-user --profileOf 0 --managed Island`

   If succeed, you will be prompted with the ID of newly created user (usually 10 or above). Remember it and replace the `<user id>` in following commands with this ID.

   If you got "Error: couldn't create User", execute `setprop fw.max_users 10` first, then retry the command above.

2. `pm path com.oasisfeng.island`

   It prints the path of the APK file of Island on your device. Copy the full path (after "`package:`"), and paste it to replace the `<path>` potion of the following command:

3. `pm install -r --user <user id> <path>`

   After the installation, proceed to the activation step: (slightly different by Android version)

4. Android 6+: `dpm set-profile-owner --user <user id> com.oasisfeng.island/.IslandDeviceAdminReceiver`</br>
   Android 5.x: `dpm set-profile-owner com.oasisfeng.island/.IslandDeviceAdminReceiver <user id>`

   If you get error message `java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_DEVICE_ADMIN`, please review the MIUI-specific steps above in "Preparation".

5. `am start-user <user id>`

6. Android 5.0.x only (not required on Android 5.1+): `settings --user 10 put secure install_non_market_apps 1`

If all goes well, Island will show the app list.


Manual setup for Island in "God mode"
---------------------------------------
**WARNING: Some Samsung users encountered [boot failure](https://github.com/oasisfeng/island/issues/75) after activating God mode. It's advised NOT to use god mod on Samsung devices.**

1. Backup all data of your non-primary users and all data of your logged-in accounts.

2. Remove all accounts and work profile in system "Settings" - "Accounts". (may vary on different devices)

3. Remove all non-primary users in system "Settings" - ("System") - "Users". (may vary on different devices)

4. Execute in ADB shell: `dpm set-device-owner com.oasisfeng.island/.IslandDeviceAdminReceiver`

   If you get error message "... Not allowed to set the device owner because there are already several **users** on the device". make sure all non-primary users are removed. You may use ADB command `pm list users` to reveal all users (including the hidden ones on some devices) and then `pm remove-user <id>` to remove them forcibly.

   If you get error message "... Not allowed to set the device owner because there are already some **accounts** on the device". make sure all accounts are removed. You may use ADB command `dumpsys account|grep -A 3 Accounts:` to reveal remaining accounts (including the hidden ones on some devices). Forcible removal of all accounts will be implemented in coming version of Island, please stay tuned.

   If you get other error message, please try executing following commands in order.  
   - `settings put global device_provisioned 0`  
   - `dpm set-device-owner com.oasisfeng.island/.IslandDeviceAdminReceiver`  
   - `settings put global device_provisioned 1`  
   *(The last command is very important, otherwise you may face status bar locked and being unable to call or SMS.)*

   Some ROM variants (e.g. MIUI) enforce extra security policy which may block the above command, if you got permission-related error message, please check the development (or security) settings to enable USB-debugging related security options, then retry the "`dpm ...`" command again.

5. Start Island app now and it will work in God mode.

God mode could even work together with normal mode in Island, giving you full control on apps both inside and outside of Island. Starting from Android 8, you can setup Island in God mode directly from Island settings - Setup, click the wrench icon on the side of Island. Prior to Android 8, you can also follow the manual steps to setup normal mode, as mentioned above.
