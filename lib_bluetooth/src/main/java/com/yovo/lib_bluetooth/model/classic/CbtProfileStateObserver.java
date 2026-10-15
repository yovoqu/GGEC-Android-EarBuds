package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * Profile 状态观察者。
 */
public interface CbtProfileStateObserver {

    default void onProfileStateChanged(@NonNull CbtDeviceProfileState state) {
    }
}
