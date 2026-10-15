package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SPP 命令结果观察者。
 */
public interface CbtCommandObserver {

    default void onCommandResult(@NonNull CbtCommandResult result) {
    }
}
