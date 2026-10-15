package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;

@FunctionalInterface
public interface KeepAliveStopHandler {
    void onStopRequested(@NonNull String sessionId);
}
