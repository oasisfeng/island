File Shuttle - Bridge for cross-border file access
====================================================

Island provides a special documents provider of Android Storage Access Framework for cross-border file access.
Currently it enables apps (supporting Android SAF) in Island to access files in mainland.
The reverse direction file access will be implemented in future version.

Setup
--------

Before the File Shuttle could be activated, a special permission must be granted with ADB via USB-connected PC.

Run this command in ADB shell:

`pm grant com.oasisfeng.island android.permission.INTERACT_ACROSS_USERS`

Then open Island app and switch to the "Discover" tab, you can activate the File Shuttle there.
