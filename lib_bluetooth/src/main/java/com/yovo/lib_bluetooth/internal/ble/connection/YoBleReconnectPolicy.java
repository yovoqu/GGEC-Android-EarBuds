package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleHciStatus;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;

/**
 * BLE 连接重试退避与 200ms GATT Close 间隔保护策略。
 */
public final class YoBleReconnectPolicy {

    private YoBleReconnectPolicy() {
    }

    public static boolean isRetryableConnectFailure(int rawStatus) {
        return YoBleHciStatus.isRetryableConnectFailure(rawStatus);
    }

    public static boolean shouldRetryOnConnectFailure(int currentAttempt,
                                                      int rawStatus,
                                                      @NonNull BleConnectionConfig config,
                                                      boolean userDisconnected,
                                                      boolean adapterEnabled,
                                                      boolean released) {
        if (userDisconnected || !adapterEnabled || released) {
            return false;
        }
        if (currentAttempt >= config.maxRetryCount()) {
            return false;
        }
        return isRetryableConnectFailure(rawStatus);
    }

    public static boolean shouldAutoReconnectOnUnexpectedDrop(boolean wasConnectedOrReady,
                                                              int currentAttempt,
                                                              @NonNull BleConnectionConfig config,
                                                              boolean userDisconnected,
                                                              boolean adapterEnabled,
                                                              boolean released) {
        return shouldAutoReconnectOnUnexpectedDrop(
                wasConnectedOrReady, currentAttempt, config,
                userDisconnected, adapterEnabled, released, 0);
    }

    public static boolean shouldAutoReconnectOnUnexpectedDrop(boolean wasConnectedOrReady,
                                                              int currentAttempt,
                                                              @NonNull BleConnectionConfig config,
                                                              boolean userDisconnected,
                                                              boolean adapterEnabled,
                                                              boolean released,
                                                              int rawStatus) {
        if (!wasConnectedOrReady || !config.autoConnect()
                || userDisconnected || !adapterEnabled || released
                || YoBleHciStatus.isLocalTerminated(rawStatus)) {
            return false;
        }
        // 丢链后的 autoConnect 重连不计入 maxRetryCount，直到用户断开、适配器关闭或释放。
        return true;
    }

    /**
     * 计算距离上次 {@code close()} 所需满足的退避等待时长（至少保证 closeDelayMillis 防竞态）。
     */
    public static long computeWaitMillis(int nextAttempt,
                                         @NonNull BleConnectionConfig config,
                                         long nowMillis,
                                         long lastCloseAtMillis) {
        int exp = Math.min(10, Math.max(0, nextAttempt - 1));
        long backoff = Math.min(config.retryMaxDelayMillis(),
                config.retryInitialDelayMillis() * (1L << exp));
        long sinceClose = lastCloseAtMillis > 0L ? Math.max(0L, nowMillis - lastCloseAtMillis) : Long.MAX_VALUE;
        long remainingCloseGap = lastCloseAtMillis > 0L
                ? Math.max(0L, config.closeDelayMillis() - sinceClose)
                : 0L;
        return Math.max(backoff, remainingCloseGap);
    }
}
