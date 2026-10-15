package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 配对状态快照。
 */
public final class CbtBondSnapshot {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtBondState state;
    private final long updatedAtMillis;

    public CbtBondSnapshot(@NonNull CbtDeviceId deviceId,
                           @NonNull CbtBondState state,
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
    public CbtBondState state() {
        return state;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
