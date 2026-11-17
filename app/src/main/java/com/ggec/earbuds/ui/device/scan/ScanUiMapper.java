package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描协议结果到界面状态与列表项映射。纯函数，不碰 View、Context、时钟。
 *
 * <p>文案只以字符串资源 id 出现；格式化留给 View 层（本类不持有 {@code Context}）。</p>
 */
public final class ScanUiMapper {

    private ScanUiMapper() {
    }

    @NonNull
    public static ScanItem map(@NonNull ScanResult result, @Nullable String connectingAddress) {
        boolean connecting = connectingAddress != null
                && !connectingAddress.isEmpty()
                && result.address().equalsIgnoreCase(connectingAddress);
        return new ScanItem(result.modelId(), result.name(), result.address(),
                result.rssi(), connecting);
    }

    @NonNull
    public static List<ScanItem> map(@NonNull List<ScanResult> results,
                                     @Nullable String connectingAddress) {
        List<ScanItem> items = new ArrayList<>(results.size());
        for (ScanResult result : results) {
            items.add(map(result, connectingAddress));
        }
        return items;
    }

    /** 由阶段、筛选与原始扫描结果派生出完整界面状态。 */
    @NonNull
    public static ScanUiState mapState(@NonNull ScanPhase phase,
                                       @NonNull String selectedFilterModelId,
                                       @NonNull List<ScanFilterOption> filterOptions,
                                       @NonNull List<ScanResult> results) {
        List<ScanItem> items = map(results, phase.connectingAddress());
        return new ScanUiState(
                phase,
                selectedFilterModelId,
                filterOptions,
                items,
                emptyTitleRes(phase, filterOptions),
                emptyBodyRes(phase, filterOptions),
                statusTextRes(phase),
                phase.scanning() ? 0 : items.size(),
                actionTextRes(phase));
    }

    /**
     * 空态标题。
     *
     * <p>目录为空时没有任何机型可筛，搜索也发现不了设备，因此无论处于哪个阶段都给专用空态
     * （design §3.3「空目录渲染无可用机型」）；文案是资源，界面层不拼。</p>
     */
    @StringRes
    public static int emptyTitleRes(@NonNull ScanPhase phase,
                                    @NonNull List<ScanFilterOption> filterOptions) {
        if (filterOptions.isEmpty()) {
            return R.string.device_scan_no_model_title;
        }
        return switch (phase) {
            case ScanPhase.Idle ignored -> R.string.device_scan_idle_title;
            case ScanPhase.Scanning ignored -> R.string.device_scan_searching;
            case ScanPhase.Stopped ignored -> R.string.device_scan_empty_title;
            case ScanPhase.Connecting ignored -> R.string.device_scan_empty_title;
            case ScanPhase.Failed ignored -> R.string.device_scan_empty_title;
        };
    }

    @StringRes
    public static int emptyBodyRes(@NonNull ScanPhase phase,
                                   @NonNull List<ScanFilterOption> filterOptions) {
        if (filterOptions.isEmpty()) {
            return R.string.device_scan_no_model_body;
        }
        return switch (phase) {
            case ScanPhase.Idle ignored -> R.string.device_scan_idle_body;
            case ScanPhase.Scanning ignored -> R.string.device_scan_searching_hint;
            case ScanPhase.Stopped ignored -> R.string.device_scan_empty_body;
            case ScanPhase.Connecting ignored -> R.string.device_scan_empty_body;
            case ScanPhase.Failed ignored -> R.string.device_scan_empty_body;
        };
    }

    @StringRes
    public static int statusTextRes(@NonNull ScanPhase phase) {
        return phase.scanning() ? R.string.device_scan_searching : R.string.device_scan_found_count;
    }

    @StringRes
    public static int actionTextRes(@NonNull ScanPhase phase) {
        return phase.scanning() ? R.string.device_scan_stop : R.string.device_scan_start;
    }
}
