package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙模块统一日志工具，通过 {@link BluetoothLogPort} 委托 YoLog 并支持模块级开关。
 */
public final class YoBluetoothLogger {
    private static volatile boolean enabled = true;
    private static volatile BluetoothLogPort port = BluetoothLogPort.UNINSTALLED;

    private YoBluetoothLogger() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setPort(@NonNull BluetoothLogPort value) {
        port = value;
    }

    public static void v(String tag, String message) {
        if (enabled) port.v(tag, message);
    }

    public static void d(String tag, String message) {
        if (enabled) port.d(tag, message);
    }

    public static void d(String tag, String message, @Nullable Throwable throwable) {
        if (enabled) port.d(tag, message, throwable);
    }

    public static void i(String tag, String message) {
        if (enabled) port.i(tag, message);
    }

    public static void i(String tag, String message, @Nullable Throwable throwable) {
        if (enabled) port.i(tag, message, throwable);
    }

    public static void w(String tag, String message) {
        if (enabled) port.w(tag, message);
    }

    public static void w(String tag, String message, @Nullable Throwable throwable) {
        if (enabled) port.w(tag, message, throwable);
    }

    public static void e(String tag, String message) {
        if (enabled) port.e(tag, message);
    }

    public static void e(String tag, String message, @Nullable Throwable throwable) {
        if (enabled) port.e(tag, message, throwable);
    }
}
