package com.ggec.earbuds.ui.device.scan;

import java.util.List;

/** 扫描页界面状态：区分初始未搜索、搜索中、已搜索无结果及筛选后无匹配态。 */
public record ScanUiState(
        boolean scanning,
        boolean hasScanned,
        String selectedFilterModelId,
        List<ScanFilterOption> filterOptions,
        List<ScanItem> results,
        int totalDiscoveredCount
) {

    public ScanUiState {
        selectedFilterModelId = selectedFilterModelId == null
                ? ScanFilterOption.ALL_MODELS : selectedFilterModelId;
        filterOptions = filterOptions == null ? List.of() : List.copyOf(filterOptions);
        results = results == null ? List.of() : List.copyOf(results);
        totalDiscoveredCount = Math.max(0, totalDiscoveredCount);
    }

    public static ScanUiState idle() {
        return new ScanUiState(
                false,
                false,
                ScanFilterOption.ALL_MODELS,
                defaultFilterOptions(),
                List.of(),
                0);
    }

    public static List<ScanFilterOption> defaultFilterOptions() {
        // 型号筛选项的唯一来源是领域目录（LoadDeviceProfiles.models() → ViewModel.filterOptions）；
        // 初始态不硬编码 HS01/HS02，避免与目录形成第二份真源而漂移。
        return List.of(new ScanFilterOption(ScanFilterOption.ALL_MODELS, ""));
    }
}
