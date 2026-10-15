package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 蓝牙模块日志端口：由 provider/lib_common 实现并委托 YoLog，
 * common / yoble / yocbt 业务层不直接依赖 lib_common。
 */
public interface BluetoothLogPort {

    BluetoothLogPort UNINSTALLED = new BluetoothLogPort() {
        @Override
        public void v(@NonNull String tag, @NonNull String message) {
            throw uninstalled();
        }

        @Override
        public void d(@NonNull String tag, @NonNull String message) {
            throw uninstalled();
        }

        @Override
        public void d(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            throw uninstalled();
        }

        @Override
        public void i(@NonNull String tag, @NonNull String message) {
            throw uninstalled();
        }

        @Override
        public void i(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            throw uninstalled();
        }

        @Override
        public void w(@NonNull String tag, @NonNull String message) {
            throw uninstalled();
        }

        @Override
        public void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            throw uninstalled();
        }

        @Override
        public void e(@NonNull String tag, @NonNull String message) {
            throw uninstalled();
        }

        @Override
        public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            throw uninstalled();
        }

        private static IllegalStateException uninstalled() {
            return new IllegalStateException(
                    "BluetoothLogPort 未安装。必须通过 YoBluetoothSDK 创建客户端。");
        }
    };

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
