package com.oasisfeng.island.shuttle;

import android.content.ComponentName;

interface IServiceConnection {
    oneway void onServiceConnected(in ComponentName name, in IBinder service);
    oneway void onServiceDisconnected(in ComponentName name);
}
