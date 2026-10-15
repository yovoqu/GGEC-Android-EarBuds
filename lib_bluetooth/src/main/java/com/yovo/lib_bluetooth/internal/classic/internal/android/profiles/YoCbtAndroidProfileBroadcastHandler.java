package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtIntentCompat;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpRuntimeState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileBroadcastApplier;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateObserver;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Profile 连接状态广播处理器。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidProfileBroadcastHandler {

    private static final String TAG = "YoCbtAndroidProfileBroadcast";

    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtProfileBroadcastApplier applier;

    public YoCbtAndroidProfileBroadcastHandler(
            @NonNull YoCbtAndroidDeviceMapper mapper,
            @NonNull YoCbtProfileBroadcastApplier applier) {
        this.mapper = mapper;
        this.applier = applier;
    }

    public void handleA2dpBroadcast(@NonNull Intent intent,
                                    @NonNull List<YoCbtProfileStateObserver> observers) {
        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                BluetoothProfile.STATE_DISCONNECTED);
        int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                BluetoothProfile.STATE_DISCONNECTED);

        YoBluetoothLogger.d(TAG, "A2DP 状态变化，设备 " + device.getAddress()
                + "：" + prevState + " -> " + state);

        YoCbtDeviceIdentity identity = mapIdentity(device);
        applier.applyA2dpConnection(
                identity,
                mapConnectionState(state),
                toA2dpRuntimeState(state),
                observers);
    }

    public void handleHeadsetBroadcast(@NonNull Intent intent,
                                       @NonNull List<YoCbtProfileStateObserver> observers) {
        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                BluetoothProfile.STATE_DISCONNECTED);
        int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                BluetoothProfile.STATE_DISCONNECTED);

        YoBluetoothLogger.d(TAG, "耳机状态变化，设备 " + device.getAddress()
                + "：" + prevState + " -> " + state);

        applier.applyHeadsetConnection(mapIdentity(device), mapConnectionState(state), observers);
    }

    public void handleHeadsetAudioBroadcast(@NonNull Intent intent,
                                            @NonNull List<YoCbtProfileStateObserver> observers) {
        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        int audioStateInt = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        YoCbtHeadsetAudioState audioState = mapAudioState(audioStateInt);

        YoBluetoothLogger.d(TAG, "耳机音频状态变化，设备 " + device.getAddress()
                + "：" + audioState);

        applier.applyHeadsetAudio(mapIdentity(device), audioState, observers);
    }

    @NonNull
    private YoCbtDeviceIdentity mapIdentity(@NonNull BluetoothDevice device) {
        YoCbtDeviceIdentity identity = mapper.map(device, null);
        if (identity != null) {
            return identity;
        }
        return new YoCbtDeviceIdentity(
                com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId.fromAddress(device.getAddress()),
                device.getAddress(),
                YoCbtAddressType.UNKNOWN,
                YoCbtDeviceType.UNKNOWN,
                device.getName());
    }

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
                return YoCbtProfileConnectionState.UNKNOWN;
        }
    }

    @NonNull
    private static YoCbtA2dpRuntimeState toA2dpRuntimeState(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
            case BluetoothProfile.STATE_DISCONNECTING:
                return YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED;
            case BluetoothProfile.STATE_CONNECTING:
                return YoCbtA2dpRuntimeState.CODEC_UNKNOWN;
            case BluetoothProfile.STATE_CONNECTED:
                return YoCbtA2dpRuntimeState.PROFILE_CONNECTED;
            default:
                return YoCbtA2dpRuntimeState.CODEC_UNKNOWN;
        }
    }

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
                return YoCbtHeadsetAudioState.UNKNOWN;
        }
    }
}
