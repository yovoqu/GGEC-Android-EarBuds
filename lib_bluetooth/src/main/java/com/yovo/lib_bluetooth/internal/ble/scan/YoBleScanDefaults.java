package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.BleScanPlan;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;

import java.util.List;

/**
 * 进程内唯一扫描器使用的策略。调用方不能改这些值。
 * 没有产品超时；系统 5 次/30 秒限流由扫描控制器的限流守卫硬拒绝（不发平台请求）。
 *
 * <p>单台设备末次广播超时判 lost 与移除是设备级过期语义（不是产品扫描超时），
 * 阈值在本类单点给出。</p>
 *
 * <p>默认扫描计划属于实现层策略，只在 {@link #plan()} 定义一次：低时延、不批处理、
 * 不上报 legacy-only、无硬件过滤（等价于"无硬件卸载过滤时客户端按帧过滤"的标准起点）。
 * 公开契约 {@link BleScanPlan} 只描述参数形状，不携带默认策略。</p>
 */
final class YoBleScanDefaults {

    /** 单台设备末次广播后判 lost 并移除的默认阈值（约 10s，可调）。 */
    static final long MATCH_LOST_TIMEOUT_MILLIS = 10_000L;

    private YoBleScanDefaults() {
    }

    @NonNull
    static BleScanPlan plan() {
        return new BleScanPlan(BleScanMode.LOW_LATENCY, 0L, false, List.of());
    }

    @NonNull
    static BleScanConfig config() {
        return config(plan());
    }

    /** 计划 → 平台配置的唯一映射：超时/lost/去重等机制策略在此固定，平台参数来自计划。 */
    @NonNull
    static BleScanConfig config(@NonNull BleScanPlan plan) {
        return BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .scanMode(plan.mode())
                .legacyOnly(plan.legacy())
                .reportDelayMillis(plan.reportDelayMs())
                .filters(plan.hardwareFilters())
                .deduplicationWindowMillis(0L)
                .matchLostTimeoutMillis(MATCH_LOST_TIMEOUT_MILLIS)
                .timeoutMillis(0L)
                .build();
    }
}
