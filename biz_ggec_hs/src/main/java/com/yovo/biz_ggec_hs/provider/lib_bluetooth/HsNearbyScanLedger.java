package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.HsNearbyDevice;

import java.util.HashMap;
import java.util.Map;

/**
 * 已上报设备的账本：按 BLE 地址索引，供 lib 的 lost 回调按地址取回并移除。
 *
 * <p>账本随一次扫描会话创建、随会话丢弃，不跨会话复用，因此没有清空入口。</p>
 */
final class HsNearbyScanLedger {

    private final Map<String, HsNearbyDevice> reported = new HashMap<>();

    synchronized void remember(@NonNull String bleAddress, @NonNull HsNearbyDevice device) {
        reported.put(bleAddress, device);
    }

    /** 取回并移除该地址上的设备；从未上报过的地址返回 null。 */
    @Nullable
    synchronized HsNearbyDevice forget(@NonNull String bleAddress) {
        return reported.remove(bleAddress);
    }
}
