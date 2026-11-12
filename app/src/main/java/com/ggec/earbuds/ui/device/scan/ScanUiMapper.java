package com.ggec.earbuds.ui.device.scan;

import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/** 扫描协议结果到列表项。四项无线身份原样带上。 */
public final class ScanUiMapper {

    private ScanUiMapper() {
    }

    public static ScanItem map(ScanResult result) {
        return new ScanItem(result.modelId(), result.bleAddress(), result.bleName(),
                result.classicAddress(), result.classicName(), result.rssi());
    }

    public static List<ScanItem> map(List<ScanResult> results) {
        List<ScanItem> items = new ArrayList<>(results.size());
        for (ScanResult result : results) {
            items.add(map(result));
        }
        return items;
    }
}
