package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP 编解码器状态查询提供者。
 */

public final class YoCbtA2dpCodecStateProvider {

    @NonNull
    private final YoCbtA2dpProfilePort profilePort;

    public YoCbtA2dpCodecStateProvider(@NonNull YoCbtA2dpProfilePort profilePort) {
        this.profilePort = profilePort;
    }

    @NonNull
    public YoResult<String> getCodecStatus(@NonNull YoCbtDeviceId deviceId) {
        return profilePort.getCodecStatus(deviceId);
    }
}
