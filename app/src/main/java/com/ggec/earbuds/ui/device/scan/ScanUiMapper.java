package com.ggec.earbuds.ui.device.scan;

import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/** 领域扫描结果 → 界面扫描项。 */
public final class ScanUiMapper {

    private ScanUiMapper() {
    }

    public static ScanItem map(ScanResult result) {
        return new ScanItem(result.modelId(), result.variantId(), result.name(),
                result.mac(), result.rssi(), result.paired());
    }

    public static List<ScanItem> map(List<ScanResult> results) {
        List<ScanItem> items = new ArrayList<>(results.size());
        for (ScanResult result : results) {
            items.add(map(result));
        }
        return List.copyOf(items);
    }
}
