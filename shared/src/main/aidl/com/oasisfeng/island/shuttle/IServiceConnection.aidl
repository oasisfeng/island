package com.oasisfeng.island.shuttle;

import android.content.ComponentName;
import com.oasisfeng.island.shuttle.IUnbinder;

interface IServiceConnection {
    oneway void onServiceConnected(in ComponentName name, in IBinder service, in IUnbinder unbinder);
    oneway void onServiceDisconnected(in ComponentName name);
    oneway void onServiceFailed();
}
