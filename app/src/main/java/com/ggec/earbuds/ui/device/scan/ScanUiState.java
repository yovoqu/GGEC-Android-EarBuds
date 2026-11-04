package com.ggec.earbuds.ui.device.scan;

import java.util.List;

/** 扫描页界面状态。 */
public record ScanUiState(boolean scanning, List<ScanItem> results) {

    public ScanUiState {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static ScanUiState idle() {
        return new ScanUiState(false, List.of());
    }
}
