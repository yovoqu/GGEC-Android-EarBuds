package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.model.RecordConnectionState;

/**
 * 连接生命周期回调。主线程派发。
 */
public interface RecordConnectionObserver {

    default void onConnectionStateChanged(@NonNull RecordConnectionState state) {
    }

    default void onConnectionFailed(@NonNull String message) {
    }
}
