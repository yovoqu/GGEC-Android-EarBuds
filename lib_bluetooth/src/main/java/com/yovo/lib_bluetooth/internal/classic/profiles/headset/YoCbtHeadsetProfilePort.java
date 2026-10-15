package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Headset Profile 平台端口抽象，封装 Android BluetoothHeadset 代理访问。
 */

public interface YoCbtHeadsetProfilePort {

    boolean isReady();

    @NonNull
    List<YoCbtDeviceId> getConnectedDevices();

    @NonNull
    YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId);

    @NonNull
    YoCbtHeadsetAudioState getAudioState(@NonNull YoCbtDeviceId deviceId);
}
