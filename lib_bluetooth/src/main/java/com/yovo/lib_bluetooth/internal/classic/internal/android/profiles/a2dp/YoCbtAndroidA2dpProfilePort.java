package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.a2dp;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy.YoCbtAndroidProfileProxyManager;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpProfilePort;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android A2DP Profile 端口实现，对接 {@link YoCbtA2dpProfilePort}。
 */

public final class YoCbtAndroidA2dpProfilePort implements YoCbtA2dpProfilePort {

    private static final String TAG = "YoCbtAndroidA2dpProfilePort";

    @NonNull
    private final YoCbtAndroidProfileProxyManager proxyManager;

    public YoCbtAndroidA2dpProfilePort(@NonNull YoCbtAndroidProfileProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public boolean isReady() {
        return proxyManager.isProxyReady(YoCbtProfileType.A2DP);
    }

    @NonNull
    @Override
    public List<YoCbtDeviceId> getConnectedDevices() {
        YoCbtAndroidA2dpPlatformProxyAdapter adapter = resolveAdapter();
        return adapter == null ? Collections.emptyList() : adapter.getConnectedDevices();
    }

    @NonNull
    @Override
    public YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAndroidA2dpPlatformProxyAdapter adapter = resolveAdapter();
        return adapter == null ? YoCbtProfileConnectionState.UNKNOWN : adapter.getConnectionState(deviceId);
    }

    @Override
    public boolean isActiveDevice(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAndroidA2dpPlatformProxyAdapter adapter = resolveAdapter();
        return adapter != null && adapter.isActiveDevice(deviceId);
    }

    @NonNull
    @Override
    public YoResult<YoCbtDeviceId> getActiveDevice() {
        BluetoothA2dp proxy = resolveProxy();
        if (proxy == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_PROXY_NOT_READY,
                    "BluetoothA2dp proxy is not ready",
                    TAG));
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_UNSUPPORTED,
                    "Active device query requires Android 11 (API 30)",
                    TAG));
        }
        try {
            BluetoothDevice activeDevice = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    activeDevice = (BluetoothDevice) BluetoothA2dp.class
                            .getMethod("getActiveDevice").invoke(proxy);
                } catch (Throwable ignored) {
                }
            }
            if (activeDevice == null) {
                return YoResult.failure(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_A2DP_ACTIVE_DEVICE_UNAVAILABLE,
                        "No active A2DP device",
                        TAG));
            }
            return YoResult.success(YoCbtDeviceId.fromAddress(activeDevice.getAddress()));
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "查询当前活跃设备失败", e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_STATE_QUERY_FAILED,
                    "Active device query failed: " + e.getMessage(),
                    TAG));
        }
    }

    @NonNull
    @Override
    public YoResult<String> getCodecStatus(@NonNull YoCbtDeviceId deviceId) {
        if (!isReady()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_A2DP_PROXY_NOT_READY,
                    "BluetoothA2dp proxy is not ready",
                    TAG));
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_UNSUPPORTED,
                    "Codec status query requires Android 11 (API 30)",
                    TAG));
        }
        return YoResult.success("Codec status unavailable on this platform");
    }

    @Nullable
    private YoCbtAndroidA2dpPlatformProxyAdapter resolveAdapter() {
        BluetoothProfile profile = proxyManager.getProxy(YoCbtProfileType.A2DP);
        if (profile == null) return null;
        return new YoCbtAndroidA2dpPlatformProxyAdapter(profile);
    }

    @Nullable
    private BluetoothA2dp resolveProxy() {
        BluetoothProfile profile = proxyManager.getProxy(YoCbtProfileType.A2DP);
        return profile instanceof BluetoothA2dp ? (BluetoothA2dp) profile : null;
    }
}
