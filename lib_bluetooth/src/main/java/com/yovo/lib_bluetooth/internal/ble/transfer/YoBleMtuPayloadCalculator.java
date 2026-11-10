package com.yovo.lib_bluetooth.internal.ble.transfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

/**
 * BLE 单次属性写入载荷上限计算器：严格遵循 Bluetooth Core Spec 最大 512 字节上限
 * （{@code Math.min(mtu - attOverhead, 512)}）。
 */
public final class YoBleMtuPayloadCalculator {

    private YoBleMtuPayloadCalculator() {
    }

    public static int effectiveMaxChunkSize(int negotiatedMtu,
                                            @Nullable Integer overriddenMtu,
                                            @NonNull BleWriteType writeType) {
        int mtu = overriddenMtu != null ? overriddenMtu : negotiatedMtu;
        int effectiveMtu = Math.max(BleConnectionSnapshot.DEFAULT_ATT_MTU, mtu);
        int overhead = writeType == BleWriteType.SIGNED ? 15 : 3;
        return Math.max(0, Math.min(effectiveMtu - overhead, BleConnectionSnapshot.MAX_ATT_WRITE_PAYLOAD));
    }
}
