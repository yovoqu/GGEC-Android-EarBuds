package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;

/**
 * 扫描会话状态机：谁在扫、属于哪一代、订阅句柄何时取消。
 *
 * <p>代次单调递增，每次 {@link #begin()} 开一代。lib 的回调携带它所属的代次，代次与当前
 * 会话不一致时 {@link #isCurrent(long)} 为 false、{@link #end(long)} 返回 false，
 * 跨会话的陈旧帧、陈旧 lost 与陈旧失败都在这里被丢弃，上层收不到旧会话的回调。</p>
 *
 * <p>订阅与取消一律在本类锁外执行：lib 的扫描器由订阅计数驱动，同步失败回调会重入会话结束
 * 路径并回调取消动作，持锁调用会自锁。</p>
 */
final class HsNearbyScanSession {

    private final Object lock = new Object();

    private long generation;
    private boolean scanning;
    @Nullable
    private BleOperationHandle subscription;

    /** 开启新一代会话，上一代立即失效。 */
    long begin() {
        synchronized (lock) {
            generation++;
            scanning = true;
            return generation;
        }
    }

    boolean isScanning() {
        synchronized (lock) {
            return scanning;
        }
    }

    /** 回调所属代次是否仍是当前会话。 */
    boolean isCurrent(long candidateGeneration) {
        synchronized (lock) {
            return isCurrentLocked(candidateGeneration);
        }
    }

    /** 回填订阅句柄。会话已在订阅期间结束时代次失效，句柄在锁外当场取消，不进入会话状态。 */
    void attach(long candidateGeneration, @NonNull BleOperationHandle handle) {
        boolean retained;
        synchronized (lock) {
            retained = isCurrentLocked(candidateGeneration);
            if (retained) {
                subscription = handle;
            }
        }
        if (!retained) {
            handle.cancel();
        }
    }

    /**
     * 回调结束本次会话。
     *
     * @return true 表示关闭的是当前代次；false 表示该回调属于已失效的旧代次
     */
    boolean end(long candidateGeneration) {
        BleOperationHandle handle;
        synchronized (lock) {
            if (!isCurrentLocked(candidateGeneration)) {
                return false;
            }
            handle = detachLocked();
        }
        cancel(handle);
        return true;
    }

    /** 外部停止：关闭当前会话，不校验代次。 */
    void stop() {
        BleOperationHandle handle;
        synchronized (lock) {
            handle = detachLocked();
        }
        cancel(handle);
    }

    private boolean isCurrentLocked(long candidateGeneration) {
        return scanning && generation == candidateGeneration;
    }

    @Nullable
    private BleOperationHandle detachLocked() {
        scanning = false;
        BleOperationHandle handle = subscription;
        subscription = null;
        return handle;
    }

    private static void cancel(@Nullable BleOperationHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }
}
