package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SPP 状态观察者。
 */
public interface CbtSppStateObserver {

    default void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
    }
}
