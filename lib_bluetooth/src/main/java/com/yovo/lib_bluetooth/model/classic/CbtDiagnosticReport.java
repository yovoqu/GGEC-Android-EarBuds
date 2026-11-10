package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 经典蓝牙模块结构化只读诊断快照（与 BLE 的 {@code BleDiagnosticReport} 对称）。
 *
 * <p>由 {@code BluetoothDiagnosticsBus} 的时间线与 dump 生成，供工单排查与自动化断言。</p>
 */
public final class CbtDiagnosticReport {

    private final long generatedAtMillis;
    @NonNull
    private final List<String> recentTimelineEntries;
    @NonNull
    private final String dumpText;

    public CbtDiagnosticReport(long generatedAtMillis,
                               @NonNull List<String> recentTimelineEntries,
                               @NonNull String dumpText) {
        this.generatedAtMillis = generatedAtMillis;
        this.recentTimelineEntries =
                Collections.unmodifiableList(new ArrayList<>(recentTimelineEntries));
        this.dumpText = Objects.requireNonNull(dumpText, "dumpText");
    }

    public long generatedAtMillis() {
        return generatedAtMillis;
    }

    @NonNull
    public List<String> recentTimelineEntries() {
        return recentTimelineEntries;
    }

    @NonNull
    public String dumpText() {
        return dumpText;
    }
}
