package com.yovo.lib_bluetooth.internal.classic.service.model;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 在进行 Profile/端点丰富化之前的 SDP 事务原始结果。
 */
public final class YoCbtSdpResult {

    @NonNull
    private final YoCbtDeviceId deviceId;
    @NonNull
    private final List<YoCbtServiceRecord> serviceRecords;
    @NonNull
    private final YoCbtSdpSource source;
    private final long resolvedAtMillis;

    public YoCbtSdpResult(@NonNull YoCbtDeviceId deviceId,
                          @NonNull List<YoCbtServiceRecord> serviceRecords,
                          @NonNull YoCbtSdpSource source,
                          long resolvedAtMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.serviceRecords = Collections.unmodifiableList(new ArrayList<>(serviceRecords));
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.resolvedAtMillis = resolvedAtMillis;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public List<YoCbtServiceRecord> getServiceRecords() {
        return serviceRecords;
    }

    @NonNull
    public List<UUID> getUuids() {
        List<UUID> uuids = new ArrayList<>(serviceRecords.size());
        for (YoCbtServiceRecord record : serviceRecords) {
            uuids.add(record.getUuid());
        }
        return Collections.unmodifiableList(uuids);
    }

    @NonNull
    public YoCbtSdpSource getSource() {
        return source;
    }

    public long getResolvedAtMillis() {
        return resolvedAtMillis;
    }

    public boolean isEmpty() {
        return serviceRecords.isEmpty();
    }

    public boolean isFromCache() {
        return source == YoCbtSdpSource.BONDED_CACHE || source == YoCbtSdpSource.CACHE_FALLBACK;
    }

    public boolean hasUuid(@NonNull UUID uuid) {
        for (YoCbtServiceRecord record : serviceRecords) {
            if (record.getUuid().equals(uuid)) return true;
        }
        return false;
    }

    @NonNull
    public static YoCbtSdpResult empty(@NonNull YoCbtDeviceId deviceId,
                                       @NonNull YoCbtSdpSource source,
                                       long resolvedAtMillis) {
        return new YoCbtSdpResult(deviceId, Collections.emptyList(), source, resolvedAtMillis);
    }
}
