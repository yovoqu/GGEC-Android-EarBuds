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

    /**
     * 记录本身没有设备名时，允许用广播名兜底，与扫描记录合并为同一份快照。
     */
    @NonNull
    public static HsScanSnapshot from(@Nullable String bleAddress,
                                      @NonNull BleScanRecord record,
                                      @Nullable String fallbackName) {
        String name = record.deviceName();
        if ((name == null || name.trim().isEmpty())
                && fallbackName != null && !fallbackName.trim().isEmpty()) {
            name = fallbackName.trim();
        }
        return HsScanSnapshot.of(
                bleAddress,
                record.serviceUuids(),
                // 跨模块取字节用复制版：rawBytesView() 是库内热路径的只读视图，
                // 协议层持有它一旦写入就会污染库的共享扫描记录。
                record.rawBytes(),
                name);
    }
}
