package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.YoLog;

/**
 * 可注入日志接口，默认 no-op。
 */
public interface KeepAliveLogger {

    KeepAliveLogger NO_OP = new KeepAliveLogger() {
    };

    /**
     * 委托 {@link YoLog} 的默认实现：未 init 时退化为 logcat DEBUG+。
     */
    KeepAliveLogger YO_LOG = new KeepAliveLogger() {
        @Override
        public void debug(@NonNull String tag, @NonNull String message) {
            YoLog.d(tag, message);
        }

        @Override
        public void info(@NonNull String tag, @NonNull String message) {
            YoLog.i(tag, message);
        }

        @Override
        public void warn(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable error) {
            YoLog.w(tag, message, error);
        }

        @Override
        public void error(@NonNull String tag, @NonNull String message,
                          @Nullable Throwable error) {
            YoLog.e(tag, message, error);
        }
    };

    default void debug(@NonNull String tag, @NonNull String message) {
    }

    default void info(@NonNull String tag, @NonNull String message) {
    }

    default void warn(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
    }

    default void error(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
    }
}
