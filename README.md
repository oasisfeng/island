# Island

Island for Android

## Build Instruction

Island depends on ["deagle" library](https://github.com/oasisfeng/deagle) and ["setupwizard" library](https://android.googlesource.com/platform/frameworks/opt/setupwizard), which must be cloned alongside Island in the same path.

```
\--
  \- island
  \- deagle
  \- setupwizard
```

This project is constructed into several modules, with **assembly** module as the build portal,
to support separate "light" build for core modules, in the form of "product flavor" in Gradle build configuration.

The **"engine"** module shares the same package name with the **"complete"** build, to inherit the profile/device owner privilege.
The **"mobile"** and other modules can be installed and updated separately alongside **"engine"** module for development convenience.
