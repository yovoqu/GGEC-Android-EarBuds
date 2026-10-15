package com.yovo.lib_bluetooth.provider.lib_common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothLogPort;
import com.yovo.lib_common.log.YoLog;

/**
 * lib_common YoLog 的蓝牙模块 Provider：全模块唯一允许 import YoLog 的类。
 */
public final class LogProvider implements BluetoothLogPort {

    public static final LogProvider INSTANCE = new LogProvider();

    private LogProvider() {
    }

    @Override
    public void v(@NonNull String tag, @NonNull String message) {
        YoLog.v(tag, message);
    }

    @Override
    public void d(@NonNull String tag, @NonNull String message) {
        YoLog.d(tag, message);
    }

    @Override
    public void d(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        YoLog.d(tag, message, throwable);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String message) {
        YoLog.i(tag, message);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        YoLog.i(tag, message, throwable);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String message) {
        YoLog.w(tag, message);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        YoLog.w(tag, message, throwable);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String message) {
        YoLog.e(tag, message);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        YoLog.e(tag, message, throwable);
    }
}
