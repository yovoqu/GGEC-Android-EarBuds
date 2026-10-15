package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 日志拦截器扩展点：返回 null 表示丢弃。
 */
public interface LogInterceptor {

    @Nullable
    LogRecord intercept(@NonNull LogRecord record);
}
