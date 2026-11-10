package com.ggec.earbuds.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.ScanResult;
import com.yovo.biz_ggec_hs.YoHsBroadcast;
import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.broadcast.Hs01Identity;
import com.yovo.biz_ggec_hs.model.broadcast.Hs02Identity;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastIdentity;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsMarket;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.HsScanSnapshots;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 将 {@code lib_bluetooth} 的 BLE 扫描结果通过 {@code biz_ggec_hs} 广播层归一化为领域 {@link ScanResult}。
 *
 * <p>同时识别 HS01（旧 128-bit Service UUID 广播）与 HS02（新 0xFFFF "HC" 厂商广播及扫描响应拼接），
 * 并保证新旧两代广播互不遮蔽、可同时呈现在扫描列表中。</p>
 */
public final class HsBroadcastScanMapper {

    static final String MODEL_HS01 = "HS01";
    static final String MODEL_HS02 = "HS02";

    private static final String VARIANT_V1 = "v1";
    private static final String VARIANT_V2 = "v2";
    private static final String VARIANT_V3 = "v3";

    private final YoHsBroadcastSession session;

    public HsBroadcastScanMapper() {
        this(YoHsBroadcast.openSession());
    }

    HsBroadcastScanMapper(@NonNull YoHsBroadcastSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void reset() {
        session.clear();
    }

    @Nullable
    public ScanResult map(@NonNull BleScanResult bleResult, @NonNull Set<String> bondedAddresses) {
        String rawBleAddress = bleResult.identity().deviceId().value();
        BleScanRecord record = bleResult.scanRecord();
        HsScanSnapshot snapshot = toSnapshot(rawBleAddress, record, bleResult.identity().displayName());
        HsBroadcastReport report = classifyReport(snapshot);
        if (!report.isHsDevice() || report.identity() == null) {
            return null;
        }
        HsBroadcastIdentity identity = report.identity();
        if (!identity.headset()) {
            return null;
        }
        String bleAddress = firstNonBlank(identity.bleAddress(), rawBleAddress);
        if (bleAddress.isEmpty()) {
            return null;
        }
        boolean paired = isBonded(bondedAddresses, bleAddress)
                || isBonded(bondedAddresses, identity.classicAddress());

        if (identity instanceof Hs01Identity hs01) {
            String name = firstNonBlank(
                    record.deviceName(),
                    bleResult.identity().displayName(),
                    MODEL_HS01);
            return new ScanResult(
                    MODEL_HS01,
                    mapHs01Variant(hs01.marketId()),
                    name,
                    bleAddress,
                    bleResult.rssi(),
                    paired);
        }
        if (identity instanceof Hs02Identity hs02) {
            String name = firstNonBlank(
                    hs02.name(),
                    record.deviceName(),
                    bleResult.identity().displayName(),
                    MODEL_HS02);
            return new ScanResult(
                    MODEL_HS02,
                    VARIANT_V1,
                    name,
                    bleAddress,
                    bleResult.rssi(),
                    paired);
        }
        return null;
    }

    @NonNull
    private HsBroadcastReport classifyReport(@NonNull HsScanSnapshot snapshot) {
        // 若单帧不含 HS02 厂商段且直接命中 HS01 Service UUID，立即作为 HS01 输出；
        // 避免同一物理设备先收到 HS02 帧后，后续纯 HS01 广播包被分片会话缓存吞并成 HS02。
        HsBroadcastReport direct = YoHsBroadcast.classify(snapshot);
        if (direct.status() == HsBroadcastReport.Status.IDENTIFIED
                && direct.identity() instanceof Hs01Identity) {
            return direct;
        }
        return session.observe(snapshot);
    }

    @NonNull
    private static HsScanSnapshot toSnapshot(@NonNull String bleAddress,
                                             @NonNull BleScanRecord record,
                                             @NonNull String displayName) {
        String recordName = record.deviceName();
        if ((recordName == null || recordName.trim().isEmpty()) && !displayName.trim().isEmpty()) {
            return HsScanSnapshot.of(
                    bleAddress,
                    record.serviceUuids(),
                    record.rawBytes(),
                    displayName.trim());
        }
        return HsScanSnapshots.from(bleAddress, record);
    }

    @NonNull
    private static String mapHs01Variant(int marketId) {
        return switch (marketId) {
            case HsMarket.TVS -> VARIANT_V2;
            case HsMarket.AUSTRALIA -> VARIANT_V3;
            default -> VARIANT_V1;
        };
    }

    private static boolean isBonded(@NonNull Set<String> bondedAddresses, @Nullable String mac) {
        if (mac == null || mac.isEmpty() || bondedAddresses.isEmpty()) {
            return false;
        }
        return bondedAddresses.contains(mac.trim().toUpperCase(Locale.ROOT));
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null) {
                String trimmed = candidate.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }
}
