package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SPP 连接观察者。会话事实只通过 {@link #onSppStateChanged}；失败活在快照的 failure 字段里。
 */
public interface CbtSppConnectionObserver {

    default void onSppDeviceResolved(@NonNull CbtDeviceIdentity identity) {
    }

    default void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
    }

    default void onSppDataReceived(@NonNull CbtSppConnectionId connectionId,
                                   @NonNull byte[] data) {
    }

    default void onSppDataSent(@NonNull CbtSppConnectionId connectionId,
                               @NonNull byte[] data) {
    }
}
