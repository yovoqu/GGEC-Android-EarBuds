package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 按蓝牙地址把 HS02 的基础帧、补充帧和完整名称拼成一次快照。
 * 只有出现 HS02 厂商段才建档；单独的设备名不会被当成 HS02。
 */
final class Hs02BroadcastAssembler {
    private static final long ENTRY_TTL_NANOS = 2_000_000_000L;
    private static final int MAX_ENTRIES = 256;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Nullable
    HsScanSnapshot fold(@Nullable HsScanSnapshot snapshot, long nowNanos) {
        prune(nowNanos);
        if (snapshot == null || snapshot.bleAddress() == null) {
            return snapshot;
        }
        Entry entry = entries.get(snapshot.bleAddress());
        // 零分配快速拒绝：既无历史建档、又无 HS02 厂商段的环境广播直接透传，避免整包 AD 解析。
        if (entry == null && !Hs02BroadcastDecoder.containsHs02Segment(snapshot.rawAdvertisingView())) {
            return snapshot;
        }
        List<byte[]> segments = new ArrayList<>();
        String name = null;
        for (HsAdFields.Field field : HsAdFields.decode(snapshot.rawAdvertisingView())) {
            if (field.type == HsAdFields.COMPLETE_NAME) {
                name = HsAdFields.decodeUtf8(field.data);
            } else if (field.type == HsAdFields.MANUFACTURER
                    && Hs02BroadcastDecoder.claimsSegment(field.data)) {
                segments.add(field.data);
            }
        }
        if (name == null) {
            String deviceName = snapshot.deviceName();
            if (deviceName != null && !deviceName.isEmpty()) {
                name = deviceName;
            }
        }

        if (entry == null && segments.isEmpty()) {
            return snapshot;
        }
        if (entry == null) {
            entry = new Entry();
            entries.put(snapshot.bleAddress(), entry);
            trimToCapacity();
        }
        entry.lastSeenNanos = nowNanos;
        if (name != null) {
            entry.name = name;
        }
        for (UUID uuid : snapshot.serviceUuids()) {
            if (uuid != null && !entry.serviceUuids.contains(uuid)) {
                entry.serviceUuids.add(uuid);
            }
        }
        mergeSegments(entry, segments);
        return entry.toSnapshot(snapshot.bleAddress());
    }

    void clear() {
        entries.clear();
    }

    private void mergeSegments(Entry entry, List<byte[]> segments) {
        List<byte[]> supplements = new ArrayList<>();
        for (byte[] segment : segments) {
            if (Hs02BroadcastDecoder.looksLikeBase(segment)) {
                if (entry.base != null && !sameBroadcast(entry.base, segment)) {
                    entry.supplements.clear();
                }
                entry.base = segment.clone();
            } else {
                supplements.add(segment);
            }
        }
        if (!supplements.isEmpty()) {
            entry.supplements.clear();
            for (byte[] supplement : supplements) {
                addDistinct(entry.supplements, supplement);
            }
        }
    }

    private static boolean sameBroadcast(byte[] previous, byte[] next) {
        if (previous.length < 11 || next.length < 11) {
            return false;
        }
        int previousNumber = (previous[6] >>> 4) & 0x0F;
        int nextNumber = (next[6] >>> 4) & 0x0F;
        return previousNumber == nextNumber
                && previous[0] == next[0]
                && previous[1] == next[1]
                && previous[2] == next[2]
                && previous[3] == next[3]
                && previous[4] == next[4]
                && previous[5] == next[5]
                && previous[7] == next[7]
                && previous[8] == next[8];
    }

    private static void addDistinct(List<byte[]> values, byte[] candidate) {
        for (byte[] value : values) {
            if (Arrays.equals(value, candidate)) {
                return;
            }
        }
        values.add(candidate.clone());
    }

    private void prune(long nowNanos) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (nowNanos - iterator.next().lastSeenNanos > ENTRY_TTL_NANOS) {
                iterator.remove();
            }
        }
    }

    private void trimToCapacity() {
        Iterator<String> iterator = entries.keySet().iterator();
        while (entries.size() > MAX_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Entry {
        private byte[] base;
        private final List<byte[]> supplements = new ArrayList<>();
        private final List<UUID> serviceUuids = new ArrayList<>();
        private String name;
        private long lastSeenNanos;

        private HsScanSnapshot toSnapshot(String bleAddress) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try {
                if (name != null) {
                    raw.write(HsAdFields.encode(
                            HsAdFields.COMPLETE_NAME, name.getBytes(StandardCharsets.UTF_8)));
                }
                if (base != null) {
                    raw.write(HsAdFields.encode(HsAdFields.MANUFACTURER, base));
                }
                for (byte[] supplement : supplements) {
                    raw.write(HsAdFields.encode(HsAdFields.MANUFACTURER, supplement));
                }
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return HsScanSnapshot.of(bleAddress, serviceUuids, raw.toByteArray(), name);
        }
    }
}
