package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;

/**
 * 把 lib_bluetooth 的扫描记录收成协议层能识别的广播快照。
 * 这是广播方向唯一接触蓝牙库的地方。
 */
public final class HsScanSnapshots {
    private HsScanSnapshots() {
    }

    @NonNull
    public static HsScanSnapshot from(@Nullable String bleAddress, @NonNull BleScanRecord record) {
        return HsScanSnapshot.of(
                bleAddress,
                record.serviceUuids(),
                record.rawBytes(),
                record.deviceName());
    }
}
