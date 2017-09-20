package com.oasisfeng.island.shuttle;

import com.oasisfeng.island.shuttle.MethodInvocation;

interface IMethodShuttle {
    void invoke(inout MethodInvocation invocation);
}
