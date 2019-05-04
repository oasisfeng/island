package com.oasisfeng.island;

import android.content.ComponentName;

/** The internal AIDL interface for controlling isolated container */
interface IContainer {
    void registerSystemService(String name, in IBinder service);
    IBinder loadService(in ComponentName service);
}
