Frequently Asked Questions
==========================

- Failed to clone app with error message:  
`"Island cannot clone apps without a proper built-in app market on your device (preferable Google Play Store)"`

This is a special requirement only on Android 5.0 devices. In most cases, your device will ship with an app store (e.g. Google Play Store) out of box.
If you received this error message, please consider upgrading your device to Android 5.1+, or switching to another ROM variant with app store built-in.

Solution for advanced users:
Execute this ADB command on USB-connected computer:

    adb -d shell settings --user 10 put secure install_non_market_apps 1

Change the number "10" with the actual user id of Island space on your device, which can be queried with `adb -d shell pm list users`.
