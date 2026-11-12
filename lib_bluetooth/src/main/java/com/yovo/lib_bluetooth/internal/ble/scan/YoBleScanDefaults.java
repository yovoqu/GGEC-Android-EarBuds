package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;

/**
 * 进程内唯一扫描器使用的策略。调用方不能改这些值。
 * 没有产品超时；系统 5 次/30 秒限流仍由扫描控制器处理。
 *
 * <p>单台设备末次广播超时判 lost 与移除是设备级过期语义（不是产品扫描超时），
 * 阈值在本类单点给出。</p>
 */
final class YoBleScanDefaults {

    /** 单台设备末次广播后判 lost 并移除的默认阈值（约 10s，可调）。 */
    static final long MATCH_LOST_TIMEOUT_MILLIS = 10_000L;

    private YoBleScanDefaults() {
    }

    @NonNull
    static BleScanConfig config() {
        return BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .legacyOnly(false)
                .reportDelayMillis(0L)
                .deduplicationWindowMillis(0L)
                .matchLostTimeoutMillis(MATCH_LOST_TIMEOUT_MILLIS)
                .timeoutMillis(0L)
                .build();
    }
}
