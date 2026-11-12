package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
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
        long firstSeenMillis;
        long lastSeenMillis;
        long lastDispatchedMillis;
        int seenCount;
        boolean notifiedFirstMatch;

        SeenEntry(@NonNull BleScanResult result, long seenMillis) {
            this.lastResult = result;
            this.firstSeenMillis = seenMillis;
            this.lastSeenMillis = seenMillis;
            this.seenCount = 1;
        }
    }

    private static final int MAX_SEEN = 256;

    private final Map<BleDeviceId, SeenEntry> seen = new LinkedHashMap<>();

    /** 当前聚合中的设备数（供诊断/统计）。 */
    public synchronized int activeDeviceCount() {
        return seen.size();
    }

    /** 是否仍在聚合中（测试/诊断用）。 */
    public synchronized boolean contains(@NonNull BleDeviceId id) {
        return seen.containsKey(id);
    }

    public synchronized void reset() {
        seen.clear();
    }

    /** 驱逐“最久未见”的设备（LRU by lastSeen），避免活跃设备因插入序被误逐。 */
    private void evictLeastRecentlySeen() {
        BleDeviceId oldestId = null;
        long oldestSeen = Long.MAX_VALUE;
        for (Map.Entry<BleDeviceId, SeenEntry> e : seen.entrySet()) {
            if (e.getValue().lastSeenMillis < oldestSeen) {
                oldestSeen = e.getValue().lastSeenMillis;
                oldestId = e.getKey();
            }
        }
        if (oldestId != null) {
            seen.remove(oldestId);
        }
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
                evictLeastRecentlySeen();
            }
            entry = new SeenEntry(result, nowMillis);
            seen.put(id, entry);
        } else {
            entry.lastResult = result;
            entry.lastSeenMillis = nowMillis;
            entry.seenCount++;
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
                out.add(result.callbackType() == BleScanCallbackType.FIRST_MATCH
                        ? result : result.withCallbackType(BleScanCallbackType.FIRST_MATCH));
            }
            return out;
        }

        // ALL_MATCHES：应用可选去重时间窗
        long dedupMs = config.deduplicationWindowMillis();
        if (dedupMs <= 0L
                || entry.lastDispatchedMillis == 0L
                || nowMillis - entry.lastDispatchedMillis >= dedupMs) {
            entry.lastDispatchedMillis = nowMillis;
            // Android 侧恒按 ALL_MATCHES 回调：已是 ALL_MATCHES 时直接复用，避免每包一次全字段复制。
            out.add(result.callbackType() == BleScanCallbackType.ALL_MATCHES
                    ? result : result.withCallbackType(BleScanCallbackType.ALL_MATCHES));
        }
        return out;
    }

    /**
     * 扫查超时未收到新广播包的设备，生成 {@link BleScanCallbackType#MATCH_LOST} 事件。
     */
    @NonNull
    public synchronized List<BleScanResult> sweepLost(@NonNull BleScanConfig config,
                                                      long nowMillis) {
        long lostTimeout = config.matchLostTimeoutMillis();
        if (lostTimeout <= 0L) {
            // 未配置过期阈值：不产生 lost（避免 ALL_MATCHES 误删所有设备）。
            return new ArrayList<>(0);
        }
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
