package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 通用单值异步回调观察者。
 *
 * @param <T> 成功回调值类型
 */
public interface BleValueObserver<T> {

    void onSuccess(@NonNull T value);

    default void onFailed(@NonNull BleFailure failure) {
    }
}
