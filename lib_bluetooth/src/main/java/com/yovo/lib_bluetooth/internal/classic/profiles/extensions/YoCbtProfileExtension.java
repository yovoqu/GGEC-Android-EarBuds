package com.yovo.lib_bluetooth.internal.classic.profiles.extensions;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 可插拔 Profile 扩展接口，用于扩展 SDK 未原生支持的 Profile。
 */

public interface YoCbtProfileExtension {

    @NonNull
    YoCbtProfileType getProfileType();

    boolean isSupported();

    @NonNull
    default YoResult<Boolean> hasCapability(@NonNull YoCbtDeviceId deviceId) {
        return YoResult.success(false);
    }

    @NonNull
    YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId);

    void release();
}
