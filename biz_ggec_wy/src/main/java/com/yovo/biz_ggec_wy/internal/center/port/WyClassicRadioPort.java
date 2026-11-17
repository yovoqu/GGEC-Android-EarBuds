package com.yovo.biz_ggec_wy.internal.center.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;

import java.util.List;

/**
 * 经典蓝牙与 SPP 通信抽象端口。
 */
public interface WyClassicRadioPort {

    interface DiscoveryListener {
        void onDeviceDiscovered(@NonNull String name, @NonNull String address, int rssi, long timestampMillis);

        void onDiscoveryFinished();

        void onDiscoveryFailed(@NonNull WyControl.Failure failure);
    }

    enum SppState {
        IDLE, CONNECTING, CONNECTED, DISCONNECTED, FAILED
    }

    interface SppStateListener {
        void onControlStateChanged(@NonNull SppState state, @Nullable WyControl.Failure failure);

        void onAudioStateChanged(@NonNull SppState state, @Nullable WyControl.Failure failure);

        void onControlDataReceived(@NonNull byte[] data);

        void onAudioDataReceived(@NonNull byte[] data);
    }

    interface SendCallback {
        void onComplete(boolean success, @Nullable WyControl.Failure failure);
    }

    interface ActionCallback {
        void onResult(boolean success, @Nullable WyControl.Failure failure);
    }

    record BondedDevice(@NonNull String name, @NonNull String address, boolean connected) {
    }

    @NonNull
    List<BondedDevice> getBondedDevices();

    void startDiscovery(@NonNull DiscoveryListener listener);

    void stopDiscovery();

    void connect(@NonNull String classicAddress, @NonNull SppStateListener listener);

    void disconnect();

    void connectAudioSpp(@NonNull ActionCallback callback);

    void disconnectAudioSpp();

    void sendControl(@NonNull byte[] data, @NonNull SendCallback callback);

    boolean isControlConnected();

    boolean isAudioConnected();

    boolean isClassicConnected(@NonNull String classicAddress);

    void release();
}
