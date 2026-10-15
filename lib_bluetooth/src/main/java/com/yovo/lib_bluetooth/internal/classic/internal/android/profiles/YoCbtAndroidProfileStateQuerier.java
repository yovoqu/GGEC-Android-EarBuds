package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtensionRegistry;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileAggregateState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateRepository;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Profile 连接状态查询器，通过代理读取各 Profile 状态。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidProfileStateQuerier {

    private static final String TAG = "YoCbtAndroidProfileStateQuerier";

    private final YoCbtProfileStateRepository stateRepository;
    @Nullable
    private final YoCbtProfileExtensionRegistry extensionRegistry;

    public YoCbtAndroidProfileStateQuerier(
            @NonNull YoCbtProfileStateRepository stateRepository,
            @Nullable YoCbtProfileExtensionRegistry extensionRegistry) {
        this.stateRepository = stateRepository;
        this.extensionRegistry = extensionRegistry;
    }

    @NonNull
    public YoResult<YoCbtProfileAggregateState> getAggregateState(
            @NonNull YoCbtProfileType type,
            @NonNull BluetoothAdapter adapter,
            @Nullable BluetoothProfile proxy) {
        if (!type.hasAndroidProfile()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                    "Profile type " + type + " has no Android profile constant",
                    TAG));
        }

        int androidState;
        try {
            androidState = adapter.getProfileConnectionState(type.getAndroidProfile());
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取 Profile 连接状态失败：" + type, e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "getProfileConnectionState threw: " + e.getMessage(),
                    TAG));
        }

        YoCbtProfileConnectionState state = mapAndroidProfileState(androidState);
        int connectedCount = countConnectedDevices(type, state, proxy);
        return YoResult.success(new YoCbtProfileAggregateState(
                type, state, connectedCount, System.currentTimeMillis()));
    }

    @NonNull
    public YoResult<YoCbtDeviceProfileState> getDeviceProfileState(
            @NonNull YoCbtDeviceId deviceId,
            @NonNull YoCbtProfileType type,
            @Nullable BluetoothProfile proxy) {
        if (type.isExtensionOnly()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                    "Profile type " + type + " requires extension query; use getDeviceProfileState(context, ...)",
                    TAG));
        }
        return queryAndPersistDeviceProfileState(deviceId, type, proxy);
    }

    @NonNull
    public YoResult<YoCbtDeviceProfileState> getDeviceProfileState(
            @NonNull Context context,
            @NonNull YoCbtDeviceId deviceId,
            @NonNull YoCbtProfileType type,
            @NonNull BluetoothAdapter adapter,
            @Nullable BluetoothProfile proxy) {
        if (type.isExtensionOnly()) {
            return queryExtensionProfileState(context, deviceId, type, adapter);
        }
        return queryAndPersistDeviceProfileState(deviceId, type, proxy);
    }

    @NonNull
    private YoResult<YoCbtDeviceProfileState> queryAndPersistDeviceProfileState(
            @NonNull YoCbtDeviceId deviceId,
            @NonNull YoCbtProfileType type,
            @Nullable BluetoothProfile proxy) {
        if (proxy == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_NOT_READY,
                    "Profile proxy is not ready for " + type
                            + "; call prepareProfileProxy() and wait for onServiceConnected",
                    TAG));
        }

        if (stateRepository.getDeviceSnapshot(deviceId) == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Device not found in snapshot store: " + deviceId,
                    TAG));
        }

        BluetoothDevice device = findBluetoothDevice(deviceId, proxy);
        if (device == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "BluetoothDevice not found for " + deviceId,
                    TAG));
        }

        int connectionState;
        try {
            connectionState = proxy.getConnectionState(device);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取连接状态失败，设备 " + deviceId + "，Profile " + type, e);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "getConnectionState threw: " + e.getMessage(),
                    TAG));
        }

        long now = System.currentTimeMillis();
        YoCbtProfileConnectionState state = mapAndroidProfileState(connectionState);
        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                type,
                state,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                now);
        if (stateRepository.applyProfileState(profileState) == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Failed to persist profile state for " + deviceId,
                    TAG));
        }
        stateRepository.persistTypedProfileState(deviceId, type, state, now);
        return YoResult.success(profileState);
    }

    @NonNull
    private YoResult<YoCbtDeviceProfileState> queryExtensionProfileState(
            @NonNull Context context,
            @NonNull YoCbtDeviceId deviceId,
            @NonNull YoCbtProfileType type,
            @NonNull BluetoothAdapter adapter) {
        if (extensionRegistry == null || !extensionRegistry.hasExtension(type)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                    "No extension registered for profile type " + type,
                    TAG));
        }

        if (stateRepository.getDeviceSnapshot(deviceId) == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Device not found in snapshot store: " + deviceId,
                    TAG));
        }

        YoResult<Boolean> connectedResult = extensionRegistry.isConnected(deviceId, type);
        if (connectedResult.isFailure()) {
            return YoResult.failure(connectedResult.getFailure());
        }

        boolean connected = Boolean.TRUE.equals(connectedResult.getValue());
        long now = System.currentTimeMillis();
        YoCbtProfileConnectionState connectionState = connected
                ? YoCbtProfileConnectionState.CONNECTED
                : YoCbtProfileConnectionState.DISCONNECTED;

        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                type,
                connectionState,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                now);
        if (stateRepository.applyProfileState(profileState) == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Failed to persist extension profile state for " + deviceId,
                    TAG));
        }
        return YoResult.success(profileState);
    }

    private static int countConnectedDevices(@NonNull YoCbtProfileType type,
                                             @NonNull YoCbtProfileConnectionState state,
                                             @Nullable BluetoothProfile proxy) {
        if (state != YoCbtProfileConnectionState.CONNECTED) {
            return 0;
        }
        if (proxy == null) {
            return 1;
        }
        try {
            List<BluetoothDevice> connected = proxy.getConnectedDevices();
            return connected != null ? connected.size() : 1;
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取已连接设备失败：" + type, e);
            return 1;
        }
    }

    @NonNull
    private static YoCbtProfileConnectionState mapAndroidProfileState(int androidState) {
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

    @Nullable
    private static BluetoothDevice findBluetoothDevice(@NonNull YoCbtDeviceId deviceId,
                                                       @NonNull BluetoothProfile proxy) {
        try {
            List<BluetoothDevice> devices = proxy.getConnectedDevices();
            if (devices != null) {
                for (BluetoothDevice device : devices) {
                    if (device != null && deviceId.value().equalsIgnoreCase(device.getAddress())) {
                        return device;
                    }
                }
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                return adapter.getRemoteDevice(deviceId.value());
            }
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "解析蓝牙设备失败: " + deviceId, e);
        }
        return null;
    }
}
