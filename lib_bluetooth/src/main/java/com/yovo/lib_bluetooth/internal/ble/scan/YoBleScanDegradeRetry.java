package com.yovo.lib_bluetooth.internal.ble.scan;

/**
 * 平台扫描失败降级重试的每代配额：底层报「硬件不支持 / 硬件资源不足」时，同一代扫描内只降级为
 * 无硬件过滤 + {@code reportDelay=0} 的软件模式重试一次，避免失败抖动导致反复重试。
 *
 * <p>对齐 Nordic Android-Scanner-Compat-Library 的降级思路；判定本身在
 * {@link YoBleScanDegradePolicy}。</p>
 */
final class YoBleScanDegradeRetry {

    private long degradedGeneration = Long.MIN_VALUE;

    /**
     * 消费本代的降级配额。
     *
     * @return {@code true} 表示本次失败可降级重试；同一代第二次调用返回 {@code false}。
     */
    boolean tryDegrade(long generation, int rawErrorCode) {
        if (!YoBleScanDegradePolicy.shouldRetryOnce(rawErrorCode, generation, degradedGeneration)) {
            return false;
        }
        degradedGeneration = generation;
        return true;
    }
}
