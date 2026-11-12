package com.yovo.biz_ggec_hs.internal.scan;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.internal.center.broadcast.Hs02Identity;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastIdentity;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastRuntime;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastSession;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsProductCatalog;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsScanSnapshot;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsScanSnapshots;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

/**
 * 产品过滤：把一帧原始广播收成对外附近设备，并判定这一帧是否交给观察者。
 *
 * <p>只保留耳机形态的设备：识别规则来自 {@code internal/broadcast}，型号/变种标识来自
 * 产品目录。这里不接触蓝牙库类型。</p>
 */
final class HsNearbyDevices {

    /** 协议认出来了、但产品目录里没有对应市场/型号时的标识：空串，不新增隐式分类。 */
    static final String UNREGISTERED_MODEL_ID = "";

    private HsNearbyDevices() {
    }

    static HsScan.Found map(HsRawAdvert advert, HsBroadcastSession session) {
        HsScanSnapshot snapshot = HsScanSnapshots.from(advert);
        HsBroadcastReport report = classify(snapshot, session);
        if (!report.isHsDevice() || report.identity() == null) {
            return null;
        }
        HsBroadcastIdentity identity = report.identity();
        if (!identity.headset()) {
            return null;
        }
        String bleAddress = firstNonBlank(identity.bleAddress(), advert.address());
        if (bleAddress.isEmpty()) {
            return null;
        }
        String modelId = HsProductCatalog.modelId(identity.marketId());
        if (modelId.isEmpty()) {
            return null;
        }
        return new HsScan.Found(
                modelId,
                new HsDevice.Radios(
                        bleAddress,
                        bleName(identity, snapshot),
                        firstNonBlank(identity.classicAddress()),
                        ""),
                advert.rssi());
    }

    /** BLE 名称只取广播本地名。HS02 厂商段里的完整名称也是这条 AD 名，不是经典蓝牙名。 */
    private static String bleName(HsBroadcastIdentity identity, HsScanSnapshot snapshot) {
        String advertised = identity instanceof Hs02Identity hs02 ? hs02.name() : null;
        return firstNonBlank(advertised, snapshot.deviceName());
    }

    /**
     * 单帧已能识别（任何代际，含 HS02 完整包）就直接采用；只有未识别、需要跨帧组装时才落入会话累积。
     */
    private static HsBroadcastReport classify(HsScanSnapshot snapshot, HsBroadcastSession session) {
        HsBroadcastReport direct = HsBroadcastRuntime.classify(snapshot);
        if (direct.status() == HsBroadcastReport.Status.IDENTIFIED) {
            return direct;
        }
        return session.observe(snapshot);
    }

    private static String firstNonBlank(String... candidates) {
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
