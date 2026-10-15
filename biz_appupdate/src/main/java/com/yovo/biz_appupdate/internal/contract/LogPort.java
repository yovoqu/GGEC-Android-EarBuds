package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 模块内日志端口：实现由 provider/lib_common 提供，业务层不感知 YoLog。
 */
public interface LogPort {

    LogPort NO_OP = new LogPort() {
    };

    default void v(@NonNull String tag, @NonNull String message) {
    }

    default void d(@NonNull String tag, @NonNull String message) {
    }

    default void i(@NonNull String tag, @NonNull String message) {
    }

    default void w(@NonNull String tag, @NonNull String message) {
    }

    default void w(@NonNull String tag, @NonNull String message,
                   @Nullable Throwable throwable) {
    }

    default void e(@NonNull String tag, @NonNull String message) {
    }

    default void e(@NonNull String tag, @NonNull String message,
                   @Nullable Throwable throwable) {
    }
}
