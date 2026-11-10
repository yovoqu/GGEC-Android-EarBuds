package com.yovo.lib_bluetooth.internal.ble.diagnostics;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoDumpable;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.util.List;
import java.util.function.Function;

/**
 * BLE 诊断报告构建器，实现 {@link YoDumpable} 输出统一结构化与可读快照。
 */
public final class YoBleDumpBuilder implements YoDumpable {

    private final boolean bleSupported;
    private final boolean adapterEnabled;
    private final boolean scanning;
    private final long nowMillis;
    private final List<BleConnectionSnapshot> activeConnections;
    private final Function<BleDeviceId, Integer> pendingQueueCountLookup;
    private final YoBleTimelineRecorder timeline;

    public YoBleDumpBuilder(boolean bleSupported,
                            boolean adapterEnabled,
                            boolean scanning,
                            long nowMillis,
                            @NonNull List<BleConnectionSnapshot> activeConnections,
                            @NonNull Function<BleDeviceId, Integer> pendingQueueCountLookup,
                            @NonNull YoBleTimelineRecorder timeline) {
        this.bleSupported = bleSupported;
        this.adapterEnabled = adapterEnabled;
        this.scanning = scanning;
        this.nowMillis = nowMillis;
        this.activeConnections = activeConnections;
        this.pendingQueueCountLookup = pendingQueueCountLookup;
        this.timeline = timeline;
    }

    @NonNull
    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== YoBleClient Diagnostic Dump ===\n");
        sb.append("timestamp=").append(nowMillis)
                .append(", bleSupported=").append(bleSupported)
                .append(", adapterEnabled=").append(adapterEnabled)
                .append(", scanning=").append(scanning)
                .append(", activeConnections=").append(activeConnections.size())
                .append('\n');
        for (int i = 0; i < activeConnections.size(); i++) {
            BleConnectionSnapshot c = activeConnections.get(i);
            int pending = pendingQueueCountLookup.apply(c.deviceId());
            sb.append("  [").append(i).append("] ")
                    .append(c.deviceId())
                    .append(" state=").append(c.state())
                    .append(" mtu=").append(c.mtu())
                    .append(" phy=(").append(c.phy().txPhy()).append('/').append(c.phy().rxPhy()).append(')')
                    .append(" bond=").append(c.bondState())
                    .append(" retries=").append(c.retryAttempt())
                    .append(" pendingQueue=").append(pending);
            if (c.lastFailure() != null) {
                sb.append(" lastError=").append(c.lastFailure().errorCode())
                        .append("(raw=").append(c.lastFailure().rawStatusCode()).append(')');
            }
            sb.append('\n');
        }
        List<String> entries = timeline.formattedEntries();
        sb.append("--- Timeline (").append(entries.size()).append(" events) ---\n");
        for (String line : entries) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }

    @NonNull
    public BleDiagnosticReport buildReport() {
        List<String> entries = timeline.formattedEntries();
        return new BleDiagnosticReport(
                nowMillis,
                bleSupported,
                adapterEnabled,
                scanning,
                activeConnections,
                entries,
                dump());
    }
}
