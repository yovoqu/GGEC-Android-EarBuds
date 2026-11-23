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

    /**
     * 计算从 {@code offset} 起、按 {@code maxChunk} 切片时当前片的结束下标（不含）。
     *
     * <p>正常情况返回 {@code Math.min(payloadLength, offset + maxChunk)}。当入参非法
     * （{@code payloadLength <= 0 || offset < 0 || maxChunk <= 0}）时，退化为
     * {@code Math.min(Math.max(0, payloadLength), Math.max(0, offset))}，即以钳位后的值收尾，
     * 不抛异常，供调用方判空片/越界并自行收尾。</p>
     *
     * <p>纯函数：无副作用、无外部状态。</p>
     */
    public static int chunkEnd(int payloadLength, int offset, int maxChunk) {
        if (payloadLength <= 0 || offset < 0 || maxChunk <= 0) {
            return Math.min(Math.max(0, payloadLength), Math.max(0, offset));
        }
        return Math.min(payloadLength, offset + maxChunk);
    }
}
