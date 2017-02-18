On most middle to high end Android devices released after 2016, Island can be setup without hassle. But still, you may be notified during the setup with error message "Sorry, your device (or ROM) is incompatible with Island". In this case, Island could still work on your device if setup manually.

If you are prompted to encrypt your device first during the setup and you don't want device decription (which may significantly degrade overall I/O performance on low-end devices), this prerequisite could also be skipped if setup manually.


Preparation
-----------
First of all, you need to connect your Android device to a computer with USB cable, and the official [ADB tool](https://developer.android.com/studio/releases/platform-tools.html) provided by Google.

To check whether the USB-connected Android device is properly recognized by your computer, type the following command in the shell (or Command Prompt on Windows):

```adb devices```

If no device is listed in the output, your Android device is not correctly recognized by the computer.

For Windows PC, [this official guide and driver list for common OEM](https://developer.android.com/studio/run/oem-usb.html) might be helpful. If it does not work out, the official Google Android USB driver should work for most Android devices, just manually install it, and select "Android ADB Interface" or "Android Composite ADB Interface".


Manual setup for Island
-----------------------
Type `adb -d shell` to open ADB shell, and execute the following commands one by one in sequence:

- ```pm create-user --profileOf 0 --managed Island```

If succeed, you will be prompted with the ID of newly created user (usually 10 or above). Remember it and replace the `<user id>` in following commands with this ID.

- ```pm install -r /data/app/com.oasisfeng.island-1/base.apk```

If you get "file not found" error, use "-2" instead of "-1" in above command and try again.

If it does not work, you may need to execute `am start-user <user-id>` first and try again then.

- For Android 6.0+: `dpm set-profile-owner --user <user id> com.oasisfeng.island/.IslandDeviceAdminReceiver`  
For Android 5.x: `dpm set-profile-owner com.oasisfeng.island/.IslandDeviceAdminReceiver <user id>`

- ```am start-user <user id>```

- ```am start com.oasisfeng.island```

If all goes well, Island will start, showing the app list.


Manual setup for Island in (experimental) God mode
------------------------------------------------------

**This "God mode" is not for normal users, and it is still experimental. Please do not setup this mode on your daily-use device.**

- Remove all accounts and work profile in Settings - Accounts.

- Execute in ADB shell: ```dpm set-device-owner com.oasisfeng.island/.IslandDeviceAdminReceiver```

If you get error message in this step, please try execute `settings put global device_provisioned 0` and try above command again, and then `settings put global device_provisioned 1`. (The last command is very important, otherwise you may face status bar locked and being unable to call or SMS.).

- Start Island now and it will work in God mode.
