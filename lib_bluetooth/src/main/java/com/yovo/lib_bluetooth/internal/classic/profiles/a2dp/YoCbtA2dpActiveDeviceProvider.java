package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP 当前活跃音频输出设备查询提供者。
 */

public final class YoCbtA2dpActiveDeviceProvider {

    @NonNull
    private final YoCbtA2dpProfilePort profilePort;

    public YoCbtA2dpActiveDeviceProvider(@NonNull YoCbtA2dpProfilePort profilePort) {
        this.profilePort = profilePort;
    }

    @NonNull
    public YoResult<YoCbtDeviceId> getActiveDevice() {
        return profilePort.getActiveDevice();
    }

    @NonNull
    public YoResult<Boolean> isActiveDevice(@NonNull YoCbtDeviceId deviceId) {
        YoResult<YoCbtDeviceId> activeResult = getActiveDevice();
        if (activeResult.isFailure()) {
            return YoResult.failure((YoCbtFailure) activeResult.getFailure());
        }
        return YoResult.success(deviceId.equals(activeResult.getValue()));
    }
}
