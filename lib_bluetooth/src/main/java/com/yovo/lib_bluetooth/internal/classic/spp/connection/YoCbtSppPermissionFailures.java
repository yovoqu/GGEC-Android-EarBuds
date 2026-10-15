package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;

/**
 * 把 SPP 连接过程中的平台 SecurityException 归到连接或扫描权限。
 * 先匹配 SCAN / ADMIN，再匹配 CONNECT / 旧 BLUETOOTH，避免 BLUETOOTH_SCAN 被当成连接权限。
 */
public final class YoCbtSppPermissionFailures {

    private YoCbtSppPermissionFailures() {
    }

    @Nullable
    public static YoCbtErrorCode fromThrowable(@Nullable Throwable throwable) {
        String text = messageChain(throwable);
        if (text.isEmpty() || !containsPermission(text)) {
            return null;
        }
        if (text.contains("BLUETOOTH_SCAN") || text.contains("BLUETOOTH_ADMIN")) {
            return YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED;
        }
        if (text.contains("BLUETOOTH_CONNECT") || text.contains("BLUETOOTH")) {
            return YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED;
        }
        return null;
    }

    private static boolean containsPermission(String text) {
        return text.contains("permission") || text.contains("Permission");
    }

    private static String messageChain(@Nullable Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            String message = current.getMessage();
            if (message != null) {
                text.append(message).append('\n');
            }
            current = current.getCause();
            depth++;
        }
        return text.toString();
    }
}
