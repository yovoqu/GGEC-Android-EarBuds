package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/** 领域扫描结果 → 界面扫描项。 */
public final class ScanUiMapper {

    private ScanUiMapper() {
    }

    public static ScanItem map(ScanResult result) {
        return new ScanItem(result.modelId(), result.variantId(), result.bleName(),
                result.bleMac(), result.rssi());
    }

    public static List<ScanItem> map(List<ScanResult> results, @Nullable String filterModelId) {
        String normalizedFilter = filterModelId == null ? ScanFilterOption.ALL_MODELS : filterModelId;
        List<ScanItem> items = new ArrayList<>(results.size());
        for (ScanResult result : results) {
            if (normalizedFilter.isEmpty() || normalizedFilter.equals(result.modelId())) {
                items.add(map(result));
            }
        }
        // 直接返回新构建的局部列表；不可变拷贝统一由 ScanUiState 构造器完成，避免每帧两次 List.copyOf。
        return items;
    }
}
