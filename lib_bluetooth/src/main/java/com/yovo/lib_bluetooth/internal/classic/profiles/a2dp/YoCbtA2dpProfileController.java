package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP Profile 控制器，管理代理生命周期、状态查询与观察者通知。
 */

public final class YoCbtA2dpProfileController {

    private static final String TAG = "YoCbtA2dpProfileController";

    @NonNull
    private final YoCbtA2dpProfilePort profilePort;

    @NonNull
    private final CopyOnWriteArrayList<YoCbtA2dpStateObserver> observers = new CopyOnWriteArrayList<>();

    public YoCbtA2dpProfileController(@NonNull YoCbtA2dpProfilePort profilePort) {
        this.profilePort = profilePort;
    }

    @NonNull
    public YoResult<List<YoCbtDeviceId>> getConnectedDevices() {
        if (!profilePort.isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_PROXY_NOT_READY,
                    "BluetoothA2dp proxy is not ready",
                    TAG));
        }
        try {
            return YoResult.success(profilePort.getConnectedDevices());
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取已连接设备失败", e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_STATE_QUERY_FAILED,
                    "Failed to list connected devices: " + e.getMessage(),
                    TAG));
        }
    }

    @NonNull
    public YoResult<YoCbtA2dpDeviceState> getDeviceState(@NonNull YoCbtDeviceId deviceId) {
        if (!profilePort.isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_PROXY_NOT_READY,
                    "BluetoothA2dp proxy is not ready",
                    TAG));
        }
        try {
            YoCbtProfileConnectionState connState = profilePort.getConnectionState(deviceId);
            boolean isActive = profilePort.isActiveDevice(deviceId);
            YoCbtA2dpRuntimeState runtimeState = deriveRuntimeState(connState, isActive);
            YoCbtA2dpDeviceState deviceState = new YoCbtA2dpDeviceState(
                    deviceId,
                    connState,
                    runtimeState,
                    System.currentTimeMillis(),
                    YoCbtProfileStateSource.ANDROID_PROFILE_PROXY);
            return YoResult.success(deviceState);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取设备状态失败，设备 " + deviceId.value(), e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_STATE_QUERY_FAILED,
                    "Failed to query device state: " + e.getMessage(),
                    TAG));
        }
    }

    public void observeState(@NonNull YoCbtA2dpStateObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(@NonNull YoCbtA2dpStateObserver observer) {
        observers.remove(observer);
    }

    @NonNull
    public YoCbtA2dpActiveDeviceProvider getActiveDeviceProvider() {
        return new YoCbtA2dpActiveDeviceProvider(profilePort);
    }

    @NonNull
    public YoCbtA2dpCodecStateProvider getCodecStateProvider() {
        return new YoCbtA2dpCodecStateProvider(profilePort);
    }

    public void notifyStateChanged(@NonNull YoCbtA2dpDeviceState state) {
        for (YoCbtA2dpStateObserver observer : observers) {
            try {
                observer.onA2dpStateChanged(state);
            } catch (Exception e) {
                YoBluetoothLogger.e(TAG, "通知期间观察者抛出异常", e);
            }
        }
    }

    @NonNull
    private static YoCbtA2dpRuntimeState deriveRuntimeState(
            @NonNull YoCbtProfileConnectionState connState,
            boolean isActive) {
        if (connState == YoCbtProfileConnectionState.CONNECTED) {
            return isActive ? YoCbtA2dpRuntimeState.ACTIVE_DEVICE : YoCbtA2dpRuntimeState.PROFILE_CONNECTED;
        }
        if (connState == YoCbtProfileConnectionState.DISCONNECTED
                || connState == YoCbtProfileConnectionState.DISCONNECTING) {
            return YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED;
        }
        return YoCbtA2dpRuntimeState.CODEC_UNKNOWN;
    }
}
