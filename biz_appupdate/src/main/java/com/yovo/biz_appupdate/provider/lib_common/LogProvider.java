package com.yovo.biz_appupdate.provider.lib_common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.LogPort;
import com.yovo.lib_common.log.YoLog;

/**
 * lib_common YoLog 的模块内 Provider：biz_appupdate 只通过 LogPort 使用日志。
 */
public final class LogProvider implements LogPort {

    @Override
    public void v(@NonNull String tag, @NonNull String message) {
        YoLog.v(tag, message);
    }

    @Override
    public void d(@NonNull String tag, @NonNull String message) {
        YoLog.d(tag, message);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String message) {
        YoLog.i(tag, message);
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
