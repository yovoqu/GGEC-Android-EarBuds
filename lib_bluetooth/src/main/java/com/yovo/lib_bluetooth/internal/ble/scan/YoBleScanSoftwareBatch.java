package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 软件批量缓存：当平台不支持硬件批量（reportDelay>0）时，把上层应投递的结果暂存，
 * 由控制器按 reportDelay 周期性冲刷。仅持有缓冲，不承担调度与状态机职责。
 */
final class YoBleScanSoftwareBatch {

    private final List<BleScanResult> pending = new ArrayList<>();
    private boolean active;

    void start() {
        active = true;
        pending.clear();
    }

    void stop() {
        active = false;
        pending.clear();
    }

    boolean isActive() {
        return active;
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    void add(@NonNull List<BleScanResult> results) {
        if (active) {
            pending.addAll(results);
        }
    }

    @NonNull
    List<BleScanResult> drain() {
        if (pending.isEmpty()) {
            return new ArrayList<>(0);
        }
        List<BleScanResult> copy = new ArrayList<>(pending);
        pending.clear();
        return copy;
    }
}
