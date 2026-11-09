package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BLE 模块结构化只读诊断快照（供工单排查、离线日志采集与自动化断言）。
 */
public final class BleDiagnosticReport {

    private final long generatedAtMillis;
    private final boolean bleSupported;
    private final boolean adapterEnabled;
    private final boolean scanning;
    private final int activeConnectionCount;
    @NonNull
    private final List<BleConnectionSnapshot> activeConnections;
    @NonNull
    private final List<String> recentTimelineEntries;
    @NonNull
    private final String dumpText;

    public BleDiagnosticReport(long generatedAtMillis,
                               boolean bleSupported,
                               boolean adapterEnabled,
                               boolean scanning,
                               @NonNull List<BleConnectionSnapshot> activeConnections,
                               @NonNull List<String> recentTimelineEntries,
                               @NonNull String dumpText) {
        this.generatedAtMillis = generatedAtMillis;
        this.bleSupported = bleSupported;
        this.adapterEnabled = adapterEnabled;
        this.scanning = scanning;
        this.activeConnections = Collections.unmodifiableList(new ArrayList<>(activeConnections));
        this.activeConnectionCount = this.activeConnections.size();
        this.recentTimelineEntries = Collections.unmodifiableList(new ArrayList<>(recentTimelineEntries));
        this.dumpText = Objects.requireNonNull(dumpText, "dumpText");
    }

    public long generatedAtMillis() {
        return generatedAtMillis;
    }

    public boolean isBleSupported() {
        return bleSupported;
    }

    public boolean isAdapterEnabled() {
        return adapterEnabled;
    }

    public boolean isScanning() {
        return scanning;
    }

    public int activeConnectionCount() {
        return activeConnectionCount;
    }

    @NonNull
    public List<BleConnectionSnapshot> activeConnections() {
        return activeConnections;
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
