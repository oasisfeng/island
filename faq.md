Frequently Asked Questions
==========================

Is Island compatible with my device?
---

If you are facing setup failure, malfunction with apps in Island or any other issues and wondering whether Island is compatible with your device / ROM, please try [TestDPC](https://play.google.com/store/apps/details?id=com.afwsamples.testdpc)，the official "Android for Work" test tool from Google.

If TestDPC also does not work properly on your device, there's a high chance that your device / ROM is incompatible with "Android for Work", please contact your device vendor or ROM maintainer, mentioning the TestDPC not working issue.

If TestDPC works flawlessly but Island does not, please [file an issue](https://github.com/oasisfeng/island/issues) with logcat attached for us to further analyze. If you are not familiar with logcat, please "Take bug report" in system "Settings - (System) - Developer options" and share the bug-report privately.


Error in setup: "Oops! Couldn't set up your work profile. Contact your IT department or try again later."
---

  If your device has "Dual Apps", "App Twin" or similar feature, it may be implemented by the same "Android for Work" infrastructure as Island. This leads to a conflict, thus you can not use both Island and these features.

  Otherwise, your device is probably incompatible with Android for Work. At present, you can only [setup Island manually](/setup.md).

Error in setup: "Custom OS installed" (Samsung-specific)
---

  Samsung enforces extra limitation beyond stock Android, forbidding work profile to be created with custom recovery installed. You can either temporarily revert back to stock recovery and flash custom recovery again after setup, or [setup Island manually](/setup.md).

Failed to clone app with error message:  
---

`"Island cannot clone apps without a proper built-in app market on your device (preferable Google Play Store)"`

  This is a special requirement only on Android 5.0 devices. In most cases, your device will ship with an app store (e.g. Google Play Store) out of box.

  If you received this error message, please consider upgrading your device to Android 5.1+, or switching to another ROM variant with app store built-in.

  **Solution for advanced user**: execute the following ADB command on USB-connected computer:

    adb -d shell settings --user 10 put secure install_non_market_apps 1

  Replace "10" above with the actual user id of Island space on your device. (use `adb -d shell pm list users` to query)

Why is my (Google) cloud backup disabled in God mode?
---

  Due to immature internal restrictions in Android system, backup service may be disabled on older version of Android in God mode. Unfortunately, this cannot be changed unless upgrading your system to Android 8+.

  - For Android 5-6, backup is always disabled when God mode is activated.
  - For Android 7.x, backup is disabled if God mod is activated and secondary user (including Island space) is created.
  - For Android 8.x, backup is always available.
