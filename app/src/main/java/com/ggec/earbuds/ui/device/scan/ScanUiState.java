package com.ggec.earbuds.ui.device.scan;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;

import java.util.List;

/** 扫描页界面状态：区分初始未搜索、搜索中、已搜索无结果及筛选后无匹配态。 */
public record ScanUiState(
        boolean scanning,
        boolean hasScanned,
        String selectedFilterModelId,
        List<ScanFilterOption> filterOptions,
        List<ScanItem> results,
        int totalDiscoveredCount,
        ScanConnectPhase connectPhase,
        String connectingAddress,
        @StringRes int emptyTitleRes,
        @StringRes int emptyBodyRes,
        @StringRes int statusTextRes,
        int statusCount,
        @StringRes int actionTextRes
) {

    public ScanUiState {
        selectedFilterModelId = (selectedFilterModelId == null || selectedFilterModelId.trim().isEmpty())
                ? "HS02" : selectedFilterModelId;
        filterOptions = filterOptions == null ? List.of() : List.copyOf(filterOptions);
        results = results == null ? List.of() : List.copyOf(results);
        totalDiscoveredCount = Math.max(0, totalDiscoveredCount);
        connectPhase = connectPhase == null ? ScanConnectPhase.IDLE : connectPhase;
        connectingAddress = connectingAddress == null ? "" : connectingAddress;
        if (emptyTitleRes == 0) {
            emptyTitleRes = ScanUiMapper.emptyTitleRes(scanning, hasScanned, totalDiscoveredCount);
        }
        if (emptyBodyRes == 0) {
            emptyBodyRes = ScanUiMapper.emptyBodyRes(scanning, hasScanned, totalDiscoveredCount);
        }
        if (statusTextRes == 0) {
            statusTextRes = ScanUiMapper.statusTextRes(scanning);
        }
        if (actionTextRes == 0) {
            actionTextRes = ScanUiMapper.actionTextRes(scanning);
        }
    }

    public ScanUiState(boolean scanning, boolean hasScanned, String selectedFilterModelId,
                       List<ScanFilterOption> filterOptions, List<ScanItem> results,
                       int totalDiscoveredCount, ScanConnectPhase connectPhase,
                       String connectingAddress) {
        this(scanning, hasScanned, selectedFilterModelId, filterOptions, results,
                totalDiscoveredCount, connectPhase, connectingAddress,
                ScanUiMapper.emptyTitleRes(scanning, hasScanned, totalDiscoveredCount),
                ScanUiMapper.emptyBodyRes(scanning, hasScanned, totalDiscoveredCount),
                ScanUiMapper.statusTextRes(scanning),
                scanning ? 0 : (results != null ? results.size() : 0),
                ScanUiMapper.actionTextRes(scanning));
    }

    public ScanUiState(boolean scanning, boolean hasScanned, String selectedFilterModelId,
                       List<ScanFilterOption> filterOptions, List<ScanItem> results,
                       int totalDiscoveredCount, ScanConnectPhase connectPhase) {
        this(scanning, hasScanned, selectedFilterModelId, filterOptions, results,
                totalDiscoveredCount, connectPhase, "");
    }

    public static ScanUiState idle() {
        return new ScanUiState(
                false,
                false,
                "HS02",
                defaultFilterOptions(),
                List.of(),
                0,
                ScanConnectPhase.IDLE,
                "",
                R.string.device_scan_idle_title,
                R.string.device_scan_idle_body,
                ScanUiMapper.statusTextRes(false),
                0,
                R.string.device_scan_start);
    }

    public static List<ScanFilterOption> defaultFilterOptions() {
        return List.of(
                new ScanFilterOption("HS01", "HS01"),
                new ScanFilterOption("HS02", "HS02"),
                new ScanFilterOption("WY01", "WY01")
        );
    }

    @NonNull
    public String resolveStatus(@NonNull Context context) {
        if (statusTextRes == 0) {
            return "";
        }
        return statusCount > 0
                ? context.getString(statusTextRes, statusCount)
                : context.getString(statusTextRes);
    }
}
