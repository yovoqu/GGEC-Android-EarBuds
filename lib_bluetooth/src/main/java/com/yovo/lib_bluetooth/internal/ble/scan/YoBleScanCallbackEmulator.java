package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨 Android API 扫描回调类型与去重节流仿真器：
 * 支持软件仿真 {@code FIRST_MATCH} / {@code MATCH_LOST} / {@code FIRST_MATCH_AND_LOST}
 * 以及毫秒级 RSSI/广播结果去重。
 */
public final class YoBleScanCallbackEmulator {

    private static final class SeenEntry {
        BleScanResult lastResult;
        long lastSeenMillis;
        long lastDispatchedMillis;
        boolean notifiedFirstMatch;

        SeenEntry(@NonNull BleScanResult result, long seenMillis) {
            this.lastResult = result;
            this.lastSeenMillis = seenMillis;
        }
    }

    private static final int MAX_SEEN = 256;

    private final Map<BleDeviceId, SeenEntry> seen = new LinkedHashMap<>();

    public synchronized void reset() {
        seen.clear();
    }

    /**
     * 处理新捕获结果并决定是否应立刻投递给上层。
     *
     * @return 若应投递，返回已打标 {@code callbackType} 的结果；若应被过滤（去重/仅 Lost/非首次），返回 empty list。
     */
    @NonNull
    public synchronized List<BleScanResult> onResult(@NonNull BleScanConfig config,
                                                     @NonNull BleScanResult result,
                                                     long nowMillis) {
        List<BleScanResult> out = new ArrayList<>(1);
        BleDeviceId id = result.identity().deviceId();
        SeenEntry entry = seen.get(id);
        boolean isNew = (entry == null);
        if (entry == null) {
            while (seen.size() >= MAX_SEEN) {
                Iterator<Map.Entry<BleDeviceId, SeenEntry>> oldest = seen.entrySet().iterator();
                if (!oldest.hasNext()) {
                    break;
                }
                oldest.next();
                oldest.remove();
            }
            entry = new SeenEntry(result, nowMillis);
            seen.put(id, entry);
        } else {
            entry.lastResult = result;
            entry.lastSeenMillis = nowMillis;
        }

        BleScanCallbackType mode = config.callbackType();
        if (mode == BleScanCallbackType.MATCH_LOST) {
            // 仅订阅丢失事件，不立即上报 found
            return out;
        }

        if (mode == BleScanCallbackType.FIRST_MATCH
                || mode == BleScanCallbackType.FIRST_MATCH_AND_LOST) {
            if (isNew || !entry.notifiedFirstMatch) {
                entry.notifiedFirstMatch = true;
                entry.lastDispatchedMillis = nowMillis;
                out.add(result.withCallbackType(BleScanCallbackType.FIRST_MATCH));
            }
            return out;
        }

        // ALL_MATCHES：应用可选去重时间窗
        long dedupMs = config.deduplicationWindowMillis();
        if (dedupMs <= 0L
                || entry.lastDispatchedMillis == 0L
                || nowMillis - entry.lastDispatchedMillis >= dedupMs) {
            entry.lastDispatchedMillis = nowMillis;
            out.add(result.withCallbackType(BleScanCallbackType.ALL_MATCHES));
        }
        return out;
    }

    /**
     * 扫查超时未收到新广播包的设备，生成 {@link BleScanCallbackType#MATCH_LOST} 事件。
     */
    @NonNull
    public synchronized List<BleScanResult> sweepLost(@NonNull BleScanConfig config,
                                                      long nowMillis) {
        BleScanCallbackType mode = config.callbackType();
        if (mode != BleScanCallbackType.MATCH_LOST
                && mode != BleScanCallbackType.FIRST_MATCH_AND_LOST) {
            return new ArrayList<>(0);
        }
        long lostTimeout = config.matchLostTimeoutMillis();
        List<BleScanResult> lostList = new ArrayList<>();
        Iterator<Map.Entry<BleDeviceId, SeenEntry>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BleDeviceId, SeenEntry> mapEntry = it.next();
            SeenEntry entry = mapEntry.getValue();
            if (nowMillis - entry.lastSeenMillis >= lostTimeout) {
                lostList.add(entry.lastResult.withCallbackType(BleScanCallbackType.MATCH_LOST));
                it.remove();
            }
        }
        return lostList;
    }
}
