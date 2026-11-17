package com.yovo.lib_bluetooth.spi.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 日志端口（SPI）：实现层与诊断总线只面向本接口写日志。
 *
 * <p>实现由 {@code provider/lib_common/log} 提供（{@code YoBtLogger}），由模块装配根注入。
 * 端口的声明在 {@code spi/} 的原因与 {@link com.yovo.lib_bluetooth.spi.permission.BluetoothPermissionPort}
 * 相同：提供方不得依赖实现层。</p>
 */
public interface LogSink {

    void v(@NonNull String tag, @NonNull String message);

    void d(@NonNull String tag, @NonNull String message);

    void d(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable);

    void i(@NonNull String tag, @NonNull String message);

    void i(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable);

    void w(@NonNull String tag, @NonNull String message);

    void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable);

    void e(@NonNull String tag, @NonNull String message);

    void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable);
}
