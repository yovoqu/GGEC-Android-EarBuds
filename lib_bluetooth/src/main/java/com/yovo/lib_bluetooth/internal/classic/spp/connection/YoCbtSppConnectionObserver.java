package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;

/**
 * 单连接观察者。会话事实只通过 {@link #onSppStateChanged}。
 */
public interface YoCbtSppConnectionObserver {
    default void onSppStateChanged(@NonNull YoCbtSppConnectionSnapshot snapshot) {
    }

    default void onSppDataReceived(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data) {
    }

    default void onSppDataSent(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data) {
    }

    default void onSppDeviceResolved(@NonNull YoCbtDeviceIdentity identity) {
    }
}
