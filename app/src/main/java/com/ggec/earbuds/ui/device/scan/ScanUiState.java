package com.ggec.earbuds.ui.device.scan;

import java.util.List;

/** 扫描页界面状态：区分初始未搜索、搜索中、已搜索无结果三种空列表态。 */
public record ScanUiState(boolean scanning, boolean hasScanned, List<ScanItem> results) {

    public ScanUiState {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static ScanUiState idle() {
        return new ScanUiState(false, false, List.of());
    }
}
