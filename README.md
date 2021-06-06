# Island

Island for Android

## Build Instructions

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

## Open API

Due to the exclusivity nature, user could only use one Android DPC app at a time, and price of switching DPC is far too heavy. To encourage active exploration and broader development in the capabilities of DPC and therefore better benefit users,
Island is devoted to build an open collaboration for community developers, either in development of this project or opening DPC capabilities to 3rd-party apps via open API. Island itself will not focus on the rich set of features, but mainly focuses on building a powerful **engine** as an open platform for much more apps from the community.

Starting from the first public version of Island, all APIs are open to 3rd-party apps with the standard runtime-permission of Android as user authorization. Developers can start building apps now to take advantage of the Island open APIs.

The protocol of all APIs are well defined and maintained in the **[class "Api"](/shared/src/main/java/com/oasisfeng/island/api/Api.java)**. 

## Contribution

If you found bugs, made minor improvements or translated the strings, please feel free to send us pull-requests.

If you are interested in improving the functionality of Island, please create an issue first to discuss your thoughts with us, we are open to collaboration in future development.

If you need new APIs for your apps to take advantage of the DPC capabilities, please feel free to create an issue to describe your app and its use case of those APIs. We are still in the early stage of building a rich set of open APIs.
