package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.headset;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 Android {@link BluetoothHeadset} 代理适配为 SDK 领域类型。
 */
@SuppressLint("MissingPermission")
public final class YoCbtAndroidHeadsetPlatformProxyAdapter {

    private static final String TAG = "YoCbtHeadsetProxyAdapter";

    private final BluetoothHeadset headsetProxy;

    /**
     * 创建包装给定 Android Profile 代理的新适配器。
     *
     * @param proxy Android {@link BluetoothProfile} 代理，须为
     *              {@link BluetoothHeadset} 实例（内部强制转换）
     * @throws IllegalArgumentException 若 {@code proxy} 不是 {@link BluetoothHeadset}
     */
    public YoCbtAndroidHeadsetPlatformProxyAdapter(@NonNull BluetoothProfile proxy) {
        if (!(proxy instanceof BluetoothHeadset)) {
            throw new IllegalArgumentException(
                    "Proxy must be an instance of BluetoothHeadset, got " + proxy.getClass().getName());
        }
        this.headsetProxy = (BluetoothHeadset) proxy;
    }

    /**
     * 返回给定设备的连接状态，映射为 SDK 类型。
     *
     * <p>委托 {@link BluetoothHeadset#getConnectionState(BluetoothDevice)}，
     * 并将 Android 整型常量映射为 {@link YoCbtProfileConnectionState}。
     *
     * @param deviceId 目标设备
     * @return 映射后的连接状态（代理返回无法识别值时为
     *         {@link YoCbtProfileConnectionState#UNKNOWN}）
     */
    @NonNull
    public YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId);
        if (device == null) {
            return YoCbtProfileConnectionState.UNKNOWN;
        }
        int state = headsetProxy.getConnectionState(device);
        return mapConnectionState(state);
    }

    /**
     * 返回当前通过 Headset/HFP Profile 连接的设备列表。
     *
     * <p>委托 {@link BluetoothHeadset#getConnectedDevices()}，并将每个
     * Android {@link BluetoothDevice} 映射为 {@link YoCbtDeviceId}。
     *
     * @return 已连接设备 ID 列表（永不为 null，可能为空）
     */
    @NonNull
    public List<YoCbtDeviceId> getConnectedDevices() {
        List<BluetoothDevice> androidDevices = headsetProxy.getConnectedDevices();
        List<YoCbtDeviceId> result = new ArrayList<>(androidDevices.size());
        for (BluetoothDevice device : androidDevices) {
            result.add(YoCbtDeviceId.fromAddress(device.getAddress()));
        }
        return result;
    }

    /**
     * 返回给定设备当前的音频（SCO）状态。
     *
     * <p>委托 {@link BluetoothHeadset#getAudioState(BluetoothDevice)}，
     * 并将 Android 整型常量映射为 {@link YoCbtHeadsetAudioState}。
     *
     * @param deviceId 目标设备
     * @return 映射后的音频状态（代理返回无法识别值时为
     *         {@link YoCbtHeadsetAudioState#UNKNOWN}）
     */
    @NonNull
    public YoCbtHeadsetAudioState getAudioState(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId);
        if (device == null) {
            return YoCbtHeadsetAudioState.UNKNOWN;
        }
        int audioState = BluetoothHeadset.STATE_AUDIO_DISCONNECTED;
        try { audioState = (int) BluetoothHeadset.class.getMethod("getAudioState", BluetoothDevice.class).invoke(headsetProxy, device); } catch (Throwable ignored) {}
        return mapAudioState(audioState);
    }

    /**
     * 检查给定设备当前是否已连接音频（SCO）。
     *
     * <p>等价于检查 {@link #getAudioState} 是否返回
     * {@link YoCbtHeadsetAudioState#AUDIO_CONNECTED}。
     *
     * @param deviceId 目标设备
     * @return 音频已连接时为 {@code true}，否则为 {@code false}
     */
    public boolean isAudioConnected(@NonNull YoCbtDeviceId deviceId) {
        return getAudioState(deviceId) == YoCbtHeadsetAudioState.AUDIO_CONNECTED;
    }

    /**
     * 在指定设备上启动语音识别（HF 指示）。
     *
     * <p>委托 {@link BluetoothHeadset#startVoiceRecognition(BluetoothDevice)}。
     *
     * @param deviceId 目标设备
     * @return 命令成功发送时为 {@code true}，否则为 {@code false}
     */
    public boolean startVoiceRecognition(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId);
        if (device == null) {
            YoBluetoothLogger.w(TAG, "启动语音识别失败：无法解析设备 " + deviceId);
            return false;
        }
        try {
            return headsetProxy.startVoiceRecognition(device);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "启动语音识别时设备 " + deviceId + " 抛出异常", e);
            return false;
        }
    }

    /**
     * 在指定设备上停止语音识别（HF 指示）。
     *
     * <p>委托 {@link BluetoothHeadset#stopVoiceRecognition(BluetoothDevice)}。
     *
     * @param deviceId 目标设备
     * @return 命令成功发送时为 {@code true}，否则为 {@code false}
     */
    public boolean stopVoiceRecognition(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId);
        if (device == null) {
            YoBluetoothLogger.w(TAG, "停止语音识别失败：无法解析设备 " + deviceId);
            return false;
        }
        try {
            return headsetProxy.stopVoiceRecognition(device);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "停止语音识别时设备 " + deviceId + " 抛出异常", e);
            return false;
        }
    }

    // ── 内部映射辅助方法 ───────────────────────────────────────

    /**
     * 将 Android {@link BluetoothProfile} 连接状态整型转换为
     * SDK 的 {@link YoCbtProfileConnectionState} 枚举。
     */
    @NonNull
    private static YoCbtProfileConnectionState mapConnectionState(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return YoCbtProfileConnectionState.DISCONNECTED;
            case BluetoothProfile.STATE_CONNECTING:
                return YoCbtProfileConnectionState.CONNECTING;
            case BluetoothProfile.STATE_CONNECTED:
                return YoCbtProfileConnectionState.CONNECTED;
            case BluetoothProfile.STATE_DISCONNECTING:
                return YoCbtProfileConnectionState.DISCONNECTING;
            default:
                YoBluetoothLogger.w(TAG, "无法识别的连接状态: " + state);
                return YoCbtProfileConnectionState.UNKNOWN;
        }
    }

    /**
     * 将 Android {@link BluetoothHeadset} 音频状态整型转换为
     * SDK 的 {@link YoCbtHeadsetAudioState} 枚举。
     */
    @NonNull
    private static YoCbtHeadsetAudioState mapAudioState(int audioState) {
        switch (audioState) {
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_DISCONNECTED;
            case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTING;
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTED;
            default:
                YoBluetoothLogger.w(TAG, "无法识别的音频状态: " + audioState);
                return YoCbtHeadsetAudioState.UNKNOWN;
        }
    }

    /**
     * 将 {@link YoCbtDeviceId} 转换为 Android {@link BluetoothDevice}。
     * <p>
     * 使用 {@link android.bluetooth.BluetoothAdapter#getRemoteDevice(String)}，
     * 此为轻量操作，无需活跃的蓝牙适配器。
     *
     * @param deviceId SDK 设备 ID
     * @return 对应的 Android BluetoothDevice；地址无效时为 {@code null}
     */
    @Nullable
    private static BluetoothDevice toAndroidDevice(@NonNull YoCbtDeviceId deviceId) {
        try {
            return android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    .getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            YoBluetoothLogger.e(TAG, "无效的设备地址: " + deviceId.value(), e);
            return null;
        }
    }
}
