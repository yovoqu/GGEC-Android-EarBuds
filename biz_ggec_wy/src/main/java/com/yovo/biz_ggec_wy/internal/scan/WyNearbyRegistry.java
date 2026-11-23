package com.yovo.biz_ggec_wy.internal.scan;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.api.WyScan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WY 附近设备表：按经典地址去重、滑动窗口平滑 RSSI、生成快照视图。
 */
final class WyNearbyRegistry {

    static final int RSSI_WINDOW = 8;

    static final class Entry {
        private final String address;
        private String name = "";
        private String modelId = "";
        private long lastSeenAtMillis;
        private final int[] rssiSamples = new int[RSSI_WINDOW];
        private int sampleCount;
        private int sampleIndex;

        Entry(String address, long lastSeenAtMillis) {
            this.address = address;
            this.lastSeenAtMillis = lastSeenAtMillis;
        }

        String address() {
            return address;
        }

        String name() {
            return name;
        }

        String modelId() {
            return modelId;
        }

        long lastSeenAtMillis() {
            return lastSeenAtMillis;
        }

        int rssi() {
            if (sampleCount == 0) {
                return 0;
            }
            int sum = 0;
            for (int i = 0; i < sampleCount; i++) {
                sum += rssiSamples[i];
            }
            return sum / sampleCount;
        }

        WyScan.Found toFound() {
            return new WyScan.Found(modelId, new WyDevice.Radios(address, name), rssi());
        }

        void update(String modelId, String name, int rssi, long timestampMillis) {
            this.modelId = modelId != null ? modelId : "";
            this.name = name != null ? name : "";
            this.lastSeenAtMillis = timestampMillis;
            this.rssiSamples[sampleIndex] = rssi;
            this.sampleIndex = (sampleIndex + 1) % RSSI_WINDOW;
            if (sampleCount < RSSI_WINDOW) {
                sampleCount++;
            }
        }
    }

    private final Object lock = new Object();
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    void update(@NonNull String modelId, @NonNull String address, @NonNull String name,
                int rssi, long timestampMillis) {
        String key = address.trim().toUpperCase(Locale.US);
        synchronized (lock) {
            Entry entry = entries.computeIfAbsent(key, k -> new Entry(k, timestampMillis));
            entry.update(modelId, name, rssi, timestampMillis);
        }
    }

    @NonNull
    List<WyScan.Found> getFoundDevices() {
        List<Entry> list;
        synchronized (lock) {
            list = new ArrayList<>(entries.values());
        }
        list.sort((a, b) -> Integer.compare(b.rssi(), a.rssi()));
        List<WyScan.Found> result = new ArrayList<>(list.size());
        for (Entry entry : list) {
            result.add(entry.toFound());
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    WyScan.View buildView(boolean scanning, @NonNull WyScan.Rule rule) {
        List<WyScan.Found> allFound = getFoundDevices();
        List<WyScan.Found> filtered = new ArrayList<>();

        Map<WyDevice.Type, Integer> countsByType = new EnumMap<>(WyDevice.Type.class);
        Map<String, Integer> countsByProject = new LinkedHashMap<>();
        Map<String, Integer> countsByModel = new LinkedHashMap<>();

        int totalInRange = allFound.size();
        for (WyScan.Found found : allFound) {
            countsByType.merge(WyDevice.Type.EARBUDS, 1, Integer::sum);
            countsByProject.merge("WY", 1, Integer::sum);
            countsByModel.merge(found.modelId(), 1, Integer::sum);

            if (matchesRule(found, rule)) {
                filtered.add(found);
            }
        }

        return new WyScan.View(
                Collections.unmodifiableList(filtered),
                Collections.unmodifiableMap(countsByType),
                Collections.unmodifiableMap(countsByProject),
                Collections.unmodifiableMap(countsByModel),
                totalInRange,
                scanning
        );
    }

    private boolean matchesRule(WyScan.Found found, WyScan.Rule rule) {
        if (rule == null) {
            return true;
        }
        if (rule.minRssi() > WyScan.Rule.MIN_RSSI_UNRESTRICTED && found.rssi() < rule.minRssi()) {
            return false;
        }
        if (rule.modelIds() != null && !rule.modelIds().isEmpty() && !rule.modelIds().contains(found.modelId())) {
            return false;
        }
        if (rule.deviceTypes() != null && !rule.deviceTypes().isEmpty() && !rule.deviceTypes().contains(WyDevice.Type.EARBUDS)) {
            return false;
        }
        if (rule.projectIds() != null && !rule.projectIds().isEmpty() && !rule.projectIds().contains("WY")) {
            return false;
        }
        return true;
    }

    void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }
}
