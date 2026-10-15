package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class KeepAliveStartResult {

    @NonNull private final KeepAliveResult result;
    @Nullable private final KeepAliveSession session;

    public KeepAliveStartResult(@NonNull KeepAliveResult result,
                                @Nullable KeepAliveSession session) {
        this.result = result;
        this.session = session;
    }

    @NonNull
    public KeepAliveResult result() {
        return result;
    }

    @Nullable
    public KeepAliveSession session() {
        return session;
    }
}
