package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;

/**
 * 日志 Sink 扩展点。
 */
public interface LogSink {

    void write(@NonNull LogRecord record);

    void flush();

    void close();
}
