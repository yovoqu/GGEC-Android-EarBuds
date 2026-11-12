package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.YoHsBroadcast;
import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.broadcast.Hs02Identity;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastIdentity;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsProductCatalog;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

/**
 * 产品过滤：把一帧 BLE 扫描收成协议附近设备，并判定这一帧是否交给上层。
 * 只保留产品目录里的耳机；目录与代际词汇都由 {@code model.broadcast} 提供。
 */
final class HsNearbyDevices {

    private HsNearbyDevices() {
    }

    @Nullable
    static HsNearbyDevice map(@NonNull BleScanResult bleResult,
                              @NonNull YoHsBroadcastSession session) {
        String rawBleAddress = bleResult.identity().deviceId().value();
        BleScanRecord record = bleResult.scanRecord();
        HsScanSnapshot snapshot =
                HsScanSnapshots.from(rawBleAddress, record, bleResult.identity().advertisingName());
        HsBroadcastReport report = classify(snapshot, session);
        if (!report.isHsDevice() || report.identity() == null) {
            return null;
        }
        HsBroadcastIdentity identity = report.identity();
        if (!identity.headset()) {
            return null;
        }
        HsProductCatalog.Product product = HsProductCatalog.byMarket(identity.marketId());
        if (product == null) {
            return null;
        }
        String bleAddress = firstNonBlank(identity.bleAddress(), rawBleAddress);
        if (bleAddress.isEmpty()) {
            return null;
        }
        return new HsNearbyDevice(
                identity.generation().name(),
                product.variantId(),
                bleName(identity, snapshot),
                bleAddress,
                bleResult.rssi());
    }

    /**
     * 名称优先级只有这一处。HS02 厂商段自带的完整名称来自本帧 TLV，是 {@link Hs02Identity#name()}
     * 的唯一来源且比库合并出的快照名更贴近该帧，因此留在首位；其后只读
     * {@link HsScanSnapshot#deviceName()}（{@link HsScanSnapshots#from} 已把广播名合入），
     * 最后退回代际名。
     */
    @NonNull
    private static String bleName(@NonNull HsBroadcastIdentity identity,
                                  @NonNull HsScanSnapshot snapshot) {
        String identityName = identity instanceof Hs02Identity hs02 ? hs02.name() : null;
        return firstNonBlank(identityName, snapshot.deviceName(), identity.generation().name());
    }

    /**
     * 单帧已能识别（任何代际，含 HS02 完整包）就直接采用；只有未识别、需要跨帧组装时才落入会话累积。
     */
    @NonNull
    private static HsBroadcastReport classify(@NonNull HsScanSnapshot snapshot,
                                              @NonNull YoHsBroadcastSession session) {
        HsBroadcastReport direct = YoHsBroadcast.classify(snapshot);
        if (direct.status() == HsBroadcastReport.Status.IDENTIFIED) {
            return direct;
        }
        return session.observe(snapshot);
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }
}
