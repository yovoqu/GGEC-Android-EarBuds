package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.a2dp;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 Android {@link BluetoothA2dp} 代理适配为 SDK 领域类型。
 */
@SuppressLint("MissingPermission")
public final class YoCbtAndroidA2dpPlatformProxyAdapter {

    private static final String TAG = "YoCbtAndroidA2dpPlatformProxyAdapter";

    @NonNull
    private final BluetoothA2dp a2dpProxy;

    /**
     * 创建新的平台代理适配器。
     *
     * @param proxy 已连接的 {@link BluetoothProfile} 代理，须为
     *              {@link BluetoothA2dp} 实例
     * @throws IllegalArgumentException 若代理不是 {@link BluetoothA2dp} 实例
     */
    public YoCbtAndroidA2dpPlatformProxyAdapter(@NonNull BluetoothProfile proxy) {
        if (!(proxy instanceof BluetoothA2dp)) {
            throw new IllegalArgumentException("Proxy must be an instance of BluetoothA2dp");
        }
        this.a2dpProxy = (BluetoothA2dp) proxy;
    }

    /**
     * 返回给定设备的 A2DP 连接状态，映射为 SDK {@link YoCbtProfileConnectionState}。
     *
     * <p>委托 {@link BluetoothA2dp#getConnectionState(BluetoothDevice)}。
     *
     * @param deviceId 要查询连接状态的设备
     * @return 映射后的连接状态；永不为 {@code null}
     */
    @NonNull
    public YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = resolveDevice(deviceId);
        if (device == null) {
            return YoCbtProfileConnectionState.UNKNOWN;
        }

        int androidState;
        try {
            androidState = a2dpProxy.getConnectionState(device);
        } catch (Exception e) {
            YoBluetoothLogger.w(TAG, "获取连接状态失败，设备 " + deviceId.value(), e);
            return YoCbtProfileConnectionState.ERROR;
        }

        return mapConnectionState(androidState);
    }

    /**
     * 返回当前通过 A2DP 连接的设备列表。
     *
     * <p>委托 {@link BluetoothA2dp#getConnectedDevices()}，并将每个
     * {@link BluetoothDevice} 映射为 {@link YoCbtDeviceId}。
     *
     * @return 已连接设备标识的不可修改列表；永不为 {@code null}，但可能为空
     */
    @NonNull
    public List<YoCbtDeviceId> getConnectedDevices() {
        List<BluetoothDevice> androidDevices;
        try {
            androidDevices = a2dpProxy.getConnectedDevices();
        } catch (Exception e) {
            YoBluetoothLogger.w(TAG, "获取已连接设备失败", e);
            return Collections.emptyList();
        }

        if (androidDevices == null || androidDevices.isEmpty()) {
            return Collections.emptyList();
        }

        List<YoCbtDeviceId> result = new ArrayList<>(androidDevices.size());
        for (BluetoothDevice device : androidDevices) {
            if (device != null) {
                result.add(YoCbtDeviceId.fromAddress(device.getAddress()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 检查给定设备是否为当前活跃的 A2DP 设备。
     *
     * <p>Android 11+ 上通过 {@link BluetoothA2dp#getActiveDevice()} 查询。
     * 更低版本平台此方法优雅地返回 {@code false}。
     *
     * @param deviceId 要检查的设备
     * @return 若该设备为活跃 A2DP 设备则为 {@code true}；否则为 {@code false}
     */
    public boolean isActiveDevice(@NonNull YoCbtDeviceId deviceId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }

        BluetoothDevice activeDevice = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activeDevice = (BluetoothDevice) BluetoothA2dp.class
                        .getMethod("getActiveDevice").invoke(a2dpProxy);
            } catch (Throwable e) {
                YoBluetoothLogger.w(TAG, "获取当前活跃设备失败", e);
                return false;
            }
        }

        if (activeDevice == null) {
            return false;
        }

        return deviceId.value().equalsIgnoreCase(activeDevice.getAddress());
    }

    // ── 内部辅助方法 ────────────────────────────────────────────

    @Nullable
    private static BluetoothDevice resolveDevice(@NonNull YoCbtDeviceId deviceId) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return null;
        try {
            return adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            YoBluetoothLogger.w(TAG, "无效的设备地址: " + deviceId.value(), e);
            return null;
        }
    }

    @NonNull
    private static YoCbtProfileConnectionState mapConnectionState(int androidState) {
        switch (androidState) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return YoCbtProfileConnectionState.DISCONNECTED;
            case BluetoothProfile.STATE_CONNECTING:
                return YoCbtProfileConnectionState.CONNECTING;
            case BluetoothProfile.STATE_CONNECTED:
                return YoCbtProfileConnectionState.CONNECTED;
            case BluetoothProfile.STATE_DISCONNECTING:
                return YoCbtProfileConnectionState.DISCONNECTING;
            default:
                return YoCbtProfileConnectionState.UNKNOWN;
        }
    }
}
