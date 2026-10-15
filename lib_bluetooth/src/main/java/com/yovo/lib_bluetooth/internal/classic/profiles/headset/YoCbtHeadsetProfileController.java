package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Headset Profile 控制器，管理代理、状态与观察者。
 */

public final class YoCbtHeadsetProfileController {

    private static final String TAG = "YoCbtHeadsetController";

    @NonNull
    private final YoCbtHeadsetProfilePort profilePort;

    private final CopyOnWriteArrayList<YoCbtHeadsetStateObserver> observers =
            new CopyOnWriteArrayList<>();

    public YoCbtHeadsetProfileController(@NonNull YoCbtHeadsetProfilePort profilePort) {
        this.profilePort = profilePort;
    }

    @NonNull
    public YoResult<List<YoCbtDeviceId>> getConnectedDevices() {
        if (!profilePort.isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_PROXY_NOT_READY,
                    "Headset profile proxy is not ready",
                    TAG));
        }
        try {
            return YoResult.success(profilePort.getConnectedDevices());
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取已连接设备失败", e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_STATE_QUERY_FAILED,
                    "Exception querying connected devices: " + e.getMessage(),
                    TAG));
        }
    }

    @NonNull
    public YoResult<YoCbtHeadsetDeviceState> getDeviceState(@NonNull YoCbtDeviceId deviceId) {
        if (!profilePort.isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_PROXY_NOT_READY,
                    "Headset profile proxy is not ready",
                    TAG));
        }
        try {
            YoCbtHeadsetDeviceState deviceState = new YoCbtHeadsetDeviceState(
                    deviceId,
                    profilePort.getConnectionState(deviceId),
                    profilePort.getAudioState(deviceId),
                    false,
                    System.currentTimeMillis(),
                    YoCbtProfileStateSource.ANDROID_PROFILE_PROXY);
            return YoResult.success(deviceState);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "查询耳机设备状态失败，设备 " + deviceId, e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_STATE_QUERY_FAILED,
                    "Exception querying device state: " + e.getMessage(),
                    TAG));
        }
    }

    @NonNull
    public YoResult<YoCbtHeadsetAudioState> getAudioState(@NonNull YoCbtDeviceId deviceId) {
        if (!profilePort.isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_PROXY_NOT_READY,
                    "Headset profile proxy is not ready",
                    TAG));
        }
        try {
            return YoResult.success(profilePort.getAudioState(deviceId));
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "查询音频状态失败，设备 " + deviceId, e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_HEADSET_AUDIO_STATE_UNKNOWN,
                    "Exception querying audio state: " + e.getMessage(),
                    TAG));
        }
    }

    public void observeState(@NonNull YoCbtHeadsetStateObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(@NonNull YoCbtHeadsetStateObserver observer) {
        observers.remove(observer);
    }

    public void notifyStateChanged(@NonNull YoCbtHeadsetDeviceState state) {
        for (YoCbtHeadsetStateObserver observer : observers) {
            try {
                observer.onHeadsetStateChanged(state);
            } catch (Exception e) {
                YoBluetoothLogger.e(TAG, "通知期间观察者抛出异常", e);
            }
        }
    }
}
