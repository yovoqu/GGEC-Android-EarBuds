package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Objects;

/**
 * 扫描页界面状态。
 *
 * <p>阶段只有一个字段 {@link #phase()}；机型筛选项与默认值都来自设备目录，本类不内置任何
 * 机型表，也不解析文案（字符串资源 id 由 {@link ScanUiMapper} 单点给出，格式化在 View 层）。</p>
 */
public record ScanUiState(
        @NonNull ScanPhase phase,
        @NonNull String selectedFilterModelId,
        @NonNull List<ScanFilterOption> filterOptions,
        @NonNull List<ScanItem> results,
        @StringRes int emptyTitleRes,
        @StringRes int emptyBodyRes,
        @StringRes int statusTextRes,
        int statusCount,
        @StringRes int actionTextRes
) {

    public ScanUiState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(selectedFilterModelId, "selectedFilterModelId");
        filterOptions = List.copyOf(Objects.requireNonNull(filterOptions, "filterOptions"));
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        requireStringResource(emptyTitleRes, "emptyTitleRes");
        requireStringResource(emptyBodyRes, "emptyBodyRes");
        requireStringResource(statusTextRes, "statusTextRes");
        requireStringResource(actionTextRes, "actionTextRes");
    }

    /** 首次进入页面时的状态：还没扫、目录为空、没有任何结果。 */
    @NonNull
    public static ScanUiState idle() {
        return ScanUiMapper.mapState(new ScanPhase.Idle(), "", List.of(), List.of());
    }

    private static void requireStringResource(@StringRes int resourceId, @NonNull String field) {
        if (resourceId == 0) {
            throw new IllegalArgumentException(field + " requires a string resource id");
        }
    }
}
