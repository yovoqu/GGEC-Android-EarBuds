package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 配对状态观察者。
 */
public interface CbtBondStateObserver {

    default void onBondStateChanged(@NonNull CbtDeviceSnapshot snapshot) {
    }
}
