package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

/**
 * 扫描结果 → 原始广播。这是广播方向唯一接触蓝牙库类型的地方：
 * 只搬运地址、名称、RSSI、服务 UUID 与广播字节，不做协议判断。
 */
final class HsRawAdverts {

    private HsRawAdverts() {
    }

    @NonNull
    static HsRawAdvert from(@NonNull BleScanResult result) {
        BleScanRecord record = result.scanRecord();
        String name = record.deviceName();
        if (name == null || name.trim().isEmpty()) {
            name = result.identity().advertisingName();
        }
        return new HsRawAdvert(
                result.identity().deviceId().value(),
                name == null ? "" : name.trim(),
                result.rssi(),
                record.serviceUuids(),
                // 跨模块取字节用复制版：rawBytesView() 是库内热路径的只读视图，
                // 协议层持有它一旦写入就会污染库的共享扫描记录。
                record.rawBytes(),
                result.isConnectable(),
                result.observedAtMillis());
    }
}
