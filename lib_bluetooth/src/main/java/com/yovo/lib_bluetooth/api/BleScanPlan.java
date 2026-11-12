package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;

import java.util.List;
import java.util.Objects;

/**
 * 平台扫描参数（机制层，不含任何产品概念）。
 *
 * <p>平台只在 {@code startScan(filters, settings, cb)} 时接受这些参数，没有"更新参数"的 API，
 * 因此变更本计划必须显式重扫（{@link YoBleClient#reconfigureScan(BleScanPlan)}）。</p>
 *
 * <p>与帧级过滤（{@link BleScanFrameFilter}）严格区分：本计划的变更会重启平台扫描；
 * 帧级谓词的变更不会。</p>
 *
 * @param mode            扫描功耗模式
 * @param reportDelayMs   批处理上报延迟，{@code 0} 表示不批处理；合法域 {@code >= 0}
 * @param legacy          是否只上报 legacy 广播
 * @param hardwareFilters 下推到平台的硬件过滤器；空列表表示无硬件过滤（客户端按帧过滤）
 */
public record BleScanPlan(@NonNull BleScanMode mode,
                          long reportDelayMs,
                          boolean legacy,
                          @NonNull List<BleScanFilter> hardwareFilters) {

    public BleScanPlan {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(hardwareFilters, "hardwareFilters");
        if (reportDelayMs < 0L) {
            throw new IllegalArgumentException(
                    "reportDelayMs must be >= 0, but was " + reportDelayMs);
        }
        hardwareFilters = List.copyOf(hardwareFilters);
    }

    /**
     * 机制层默认计划：低时延、不批处理、仅上报 legacy 之外的全部广播、无硬件过滤。
     * 这是"无硬件卸载过滤时客户端按帧过滤"的标准起点。
     */
    @NonNull
    public static BleScanPlan defaults() {
        return new BleScanPlan(BleScanMode.LOW_LATENCY, 0L, false, List.of());
    }
}
