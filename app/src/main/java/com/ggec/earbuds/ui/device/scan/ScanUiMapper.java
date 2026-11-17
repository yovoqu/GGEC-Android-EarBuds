package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/** 扫描协议结果到界面状态与列表项映射。纯函数，不碰 View、Context、时钟。 */
public final class ScanUiMapper {

    private ScanUiMapper() {
    }

    public static ScanItem map(ScanResult result) {
        return map(result, null);
    }

    public static ScanItem map(ScanResult result, @Nullable String connectingAddress) {
        boolean connecting = connectingAddress != null
                && !connectingAddress.isEmpty()
                && result.address().equalsIgnoreCase(connectingAddress);
        return new ScanItem(result.modelId(), result.name(), result.address(),
                result.rssi(), connecting);
    }

    public static List<ScanItem> map(List<ScanResult> results) {
        return map(results, null);
    }

    public static List<ScanItem> map(List<ScanResult> results, @Nullable String connectingAddress) {
        List<ScanItem> items = new ArrayList<>(results.size());
        for (ScanResult result : results) {
            items.add(map(result, connectingAddress));
        }
        return items;
    }

    @NonNull
    public static ScanUiState mapState(boolean scanning,
                                       boolean hasScanned,
                                       @Nullable String selectedFilterModelId,
                                       @Nullable List<ScanFilterOption> filterOptions,
                                       @Nullable List<ScanResult> visible,
                                       @Nullable ScanConnectPhase connectPhase,
                                       @Nullable String connectingAddress) {
        List<ScanItem> items = map(visible == null ? List.of() : visible, connectingAddress);
        int totalDiscovered = visible == null ? 0 : visible.size();
        return new ScanUiState(
                scanning,
                hasScanned,
                selectedFilterModelId,
                filterOptions,
                items,
                totalDiscovered,
                connectPhase,
                connectingAddress,
                emptyTitleRes(scanning, hasScanned, totalDiscovered),
                emptyBodyRes(scanning, hasScanned, totalDiscovered),
                statusTextRes(scanning),
                scanning ? 0 : items.size(),
                actionTextRes(scanning));
    }

    @StringRes
    public static int emptyTitleRes(boolean scanning, boolean hasScanned, int totalDiscoveredCount) {
        if (totalDiscoveredCount > 0) {
            return R.string.device_scan_filter_empty_title;
        } else if (scanning) {
            return R.string.device_scan_searching;
        } else if (!hasScanned) {
            return R.string.device_scan_idle_title;
        } else {
            return R.string.device_scan_empty_title;
        }
    }

    @StringRes
    public static int emptyBodyRes(boolean scanning, boolean hasScanned, int totalDiscoveredCount) {
        if (totalDiscoveredCount > 0) {
            return R.string.device_scan_filter_empty_body;
        } else if (scanning) {
            return R.string.device_scan_searching_hint;
        } else if (!hasScanned) {
            return R.string.device_scan_idle_body;
        } else {
            return R.string.device_scan_empty_body;
        }
    }

    @StringRes
    public static int statusTextRes(boolean scanning) {
        return scanning ? R.string.device_scan_searching : R.string.device_scan_found_count;
    }

    @StringRes
    public static int actionTextRes(boolean scanning) {
        return scanning ? R.string.device_scan_stop : R.string.device_scan_start;
    }
}
