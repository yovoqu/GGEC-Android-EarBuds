package com.yovo.lib_bluetooth.internal.ble.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoEventTimeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * BLE 事件环形时间线记录器：自动容量裁剪与安全 Hex 摘要（防止日志膨胀与明文敏感数据泄漏）。
 */
public final class YoBleTimelineRecorder {

    private static final String TAG = "YoBleTimeline";
    private static final int MAX_HEX_PREVIEW_BYTES = 16;

    @NonNull
    private final YoEventTimeline timeline;

    public YoBleTimelineRecorder(int capacity) {
        this.timeline = new YoEventTimeline(capacity);
    }

    public void record(@NonNull String domainTag, @NonNull String message) {
        timeline.record(domainTag, message);
        YoBluetoothLogger.d(TAG, "[" + domainTag + "] " + message);
    }

    public int size() {
        return timeline.size();
    }

    @NonNull
    public List<String> formattedEntries() {
        List<YoEventTimeline.TimedEvent> events = timeline.snapshot();
        List<String> lines = new ArrayList<>(events.size());
        for (YoEventTimeline.TimedEvent e : events) {
            lines.add(e.toString());
        }
        return Collections.unmodifiableList(lines);
    }

    public void clear() {
        timeline.clear();
    }

    /** 截断脱敏的 Hex 摘要格式，最多展示前 16 字节。 */
    @NonNull
    public static String summarizeBytes(@Nullable byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        if (bytes.length == 0) {
            return "len=0 []";
        }
        int preview = Math.min(bytes.length, MAX_HEX_PREVIEW_BYTES);
        StringBuilder sb = new StringBuilder();
        sb.append("len=").append(bytes.length).append(" [");
        for (int i = 0; i < preview; i++) {
            if (i > 0) sb.append(' ');
            int v = bytes[i] & 0xFF;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase(Locale.ROOT));
        }
        if (bytes.length > preview) {
            sb.append(" ...(+").append(bytes.length - preview).append("B)");
        }
        sb.append(']');
        return sb.toString();
    }
}
