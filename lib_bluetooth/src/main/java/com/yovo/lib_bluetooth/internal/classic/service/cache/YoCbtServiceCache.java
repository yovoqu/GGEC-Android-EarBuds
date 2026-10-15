package com.yovo.lib_bluetooth.internal.classic.service.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRecord;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 按设备线程安全地缓存 {@link YoCbtSdpRecord} 条目。
 */
public final class YoCbtServiceCache {

    private final YoCbtServiceCachePolicy policy;
    private final Map<YoCbtDeviceId, YoCbtSdpRecord> records = new LinkedHashMap<>();

    public YoCbtServiceCache(@NonNull YoCbtServiceCachePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public YoCbtServiceCache() {
        this(YoCbtServiceCachePolicy.defaults());
    }

    public synchronized void put(@NonNull YoCbtSdpResult result) {
        Objects.requireNonNull(result, "result must not be null");
        records.put(result.getDeviceId(), YoCbtSdpRecord.of(result));
    }

    public synchronized void put(@NonNull YoCbtSdpRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.put(record.getDeviceId(), record);
    }

    @Nullable
    public synchronized YoCbtSdpRecord get(@NonNull YoCbtDeviceId deviceId) {
        return records.get(deviceId);
    }

    @Nullable
    public synchronized YoCbtSdpResult getResult(@NonNull YoCbtDeviceId deviceId) {
        YoCbtSdpRecord record = records.get(deviceId);
        return record == null ? null : record.getResult();
    }

    /**
     * 返回可用于回退且未过期的缓存结果，或 {@code null}。
     */
    @Nullable
    public synchronized YoCbtSdpResult getFallback(@NonNull YoCbtDeviceId deviceId) {
        YoCbtSdpRecord record = records.get(deviceId);
        if (record == null) return null;
        YoCbtSdpResult result = record.getResult();
        if (result.isEmpty()) return null;
        if (!isSourceAllowed(result.getSource())) return null;
        if (isStale(result.getResolvedAtMillis())) return null;
        return result;
    }

    public synchronized void remove(@NonNull YoCbtDeviceId deviceId) {
        records.remove(deviceId);
    }

    public synchronized void clear() {
        records.clear();
    }

    public synchronized boolean contains(@NonNull YoCbtDeviceId deviceId) {
        return records.containsKey(deviceId);
    }

    private boolean isSourceAllowed(@NonNull YoCbtSdpSource source) {
        if (source == YoCbtSdpSource.BONDED_CACHE) {
            return policy.isAllowBondedCache();
        }
        if (source == YoCbtSdpSource.LIVE_SDP || source == YoCbtSdpSource.CACHE_FALLBACK) {
            return policy.isAllowLiveCache();
        }
        return source == YoCbtSdpSource.CONFIG_INJECTED;
    }

    private boolean isStale(long resolvedAtMillis) {
        if (resolvedAtMillis <= 0) return true;
        return System.currentTimeMillis() - resolvedAtMillis > policy.getMaxAgeMillis();
    }
}
