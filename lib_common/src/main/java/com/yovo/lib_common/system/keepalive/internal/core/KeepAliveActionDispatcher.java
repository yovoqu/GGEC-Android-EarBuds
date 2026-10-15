package com.yovo.lib_common.system.keepalive.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveStopHandler;
import com.yovo.lib_common.log.YoLog;

public final class KeepAliveActionDispatcher {

    private KeepAliveActionDispatcher() {}

    public static void dispatchUserStop(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(sessionId);
        if (entry == null) {
            return;
        }
        KeepAliveStopHandler handler = entry.stopHandler();
        if (handler != null) {
            handler.onStopRequested(sessionId);
        } else {
            YoLog.w("KeepAliveActionDispatcher",
                    "用户请求停止，但会话没有停止处理器: " + sessionId);
        }
    }
}
