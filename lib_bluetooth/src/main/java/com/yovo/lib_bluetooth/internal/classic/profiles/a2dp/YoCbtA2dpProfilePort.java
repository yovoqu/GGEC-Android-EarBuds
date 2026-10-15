package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP Profile 平台端口抽象，封装 Android BluetoothA2dp 代理访问。
 */

public interface YoCbtA2dpProfilePort {

    boolean isReady();

    @NonNull
    List<YoCbtDeviceId> getConnectedDevices();

    @NonNull
    YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId);

    boolean isActiveDevice(@NonNull YoCbtDeviceId deviceId);

    @NonNull
    YoResult<YoCbtDeviceId> getActiveDevice();

    @NonNull
    YoResult<String> getCodecStatus(@NonNull YoCbtDeviceId deviceId);
}
