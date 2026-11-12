package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.List;
import java.util.UUID;

/**
 * BLE 多维扫描过滤软件匹配器：补齐硬件滤波在低版本/不同厂商 ROM 上对子串名称、Mask、MinRssi 的差异。
 */
public final class YoBleScanFilterMatcher {

    private YoBleScanFilterMatcher() {
    }

    public static boolean matchesConfig(@NonNull BleScanConfig config,
                                        @NonNull BleScanResult result) {
        if (config.requireConnectable() && !result.isConnectable()) {
            return false;
        }
        List<BleScanFilter> filters = config.filters();
        if (filters.isEmpty()) {
            return true;
        }
        for (BleScanFilter filter : filters) {
            if (matchesFilter(filter, result)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesFilter(@NonNull BleScanFilter filter,
                                        @NonNull BleScanResult result) {
        if (filter.requireConnectable() && !result.isConnectable()) {
            return false;
        }
        if (filter.minRssi() != null && result.rssi() < filter.minRssi()) {
            return false;
        }
        if (filter.deviceAddress() != null
                && !filter.deviceAddress().equalsIgnoreCase(result.identity().deviceId().value())) {
            return false;
        }

        BleScanRecord record = result.scanRecord();
        String effectiveName = resolveEffectiveName(result);

        if (filter.deviceName() != null) {
            if (!filter.deviceName().equals(effectiveName)) {
                return false;
            }
        }
        if (filter.deviceNameContains() != null) {
            if (effectiveName == null || !effectiveName.contains(filter.deviceNameContains())) {
                return false;
            }
        }
        if (filter.serviceUuid() != null && !matchesAnyUuid(
                filter.serviceUuid(), filter.serviceUuidMask(), record.serviceUuids())) {
            return false;
        }
        if (filter.solicitationUuid() != null && !matchesAnyUuid(
                filter.solicitationUuid(), filter.solicitationUuidMask(),
                record.serviceSolicitationUuids())) {
            return false;
        }
        if (filter.serviceDataUuid() != null) {
            byte[] actualData = record.getServiceDataView(filter.serviceDataUuid());
            if (!matchesMaskedBytes(filter.serviceData(), filter.serviceDataMask(), actualData)) {
                return false;
            }
        }
        if (filter.manufacturerId() != null) {
            byte[] actualData = record.getManufacturerSpecificDataView(filter.manufacturerId());
            if (!matchesMaskedBytes(
                    filter.manufacturerData(), filter.manufacturerDataMask(), actualData)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static String resolveEffectiveName(@NonNull BleScanResult result) {
        String recordName = result.scanRecord().deviceName();
        if (recordName != null && !recordName.isEmpty()) {
            return recordName;
        }
        String identityName = result.identity().advertisingName();
        return identityName.isEmpty() ? null : identityName;
    }

    private static boolean matchesAnyUuid(@NonNull UUID target,
                                          @Nullable UUID mask,
                                          @NonNull List<UUID> candidates) {
        for (UUID candidate : candidates) {
            if (matchesMaskedUuid(target, mask, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesMaskedUuid(@NonNull UUID target,
                                            @Nullable UUID mask,
                                            @Nullable UUID candidate) {
        if (candidate == null) {
            return false;
        }
        if (mask == null) {
            return target.equals(candidate);
        }
        long targetMsb = target.getMostSignificantBits() & mask.getMostSignificantBits();
        long candidateMsb = candidate.getMostSignificantBits() & mask.getMostSignificantBits();
        long targetLsb = target.getLeastSignificantBits() & mask.getLeastSignificantBits();
        long candidateLsb = candidate.getLeastSignificantBits() & mask.getLeastSignificantBits();
        return targetMsb == candidateMsb && targetLsb == candidateLsb;
    }

    public static boolean matchesMaskedBytes(@Nullable byte[] expected,
                                             @Nullable byte[] mask,
                                             @Nullable byte[] actual) {
        if (actual == null) {
            return false;
        }
        if (expected == null) {
            return true;
        }
        if (actual.length < expected.length) {
            return false;
        }
        if (mask == null) {
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != actual[i]) {
                    return false;
                }
            }
            return true;
        }
        if (mask.length < expected.length) {
            // 掩码比期望数据短：无法完整比较，按不匹配处理（避免越界，且不误放行）。
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((expected[i] & mask[i]) != (actual[i] & mask[i])) {
                return false;
            }
        }
        return true;
    }
}
