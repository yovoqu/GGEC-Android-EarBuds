package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * ACL 链路快照。
 */
public final class CbtAclLinkSnapshot {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtAclLinkState state;
    private final long updatedAtMillis;

    public CbtAclLinkSnapshot(@NonNull CbtDeviceId deviceId,
                              @NonNull CbtAclLinkState state,
                              long updatedAtMillis) {
        this.deviceId = deviceId;
        this.state = state;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtAclLinkState state() {
        return state;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
