package com.ggec.earbuds.provider.biz_ggec_record;

import com.ggec.earbuds.domain.record.model.RecordConnectionState;
import com.ggec.earbuds.domain.record.model.RecordLinkStats;
import com.ggec.earbuds.domain.record.model.RecordNearbyDevice;
import com.ggec.earbuds.domain.record.model.RecordScanFailure;
import com.ggec.earbuds.domain.record.model.RecordVideoCodec;

/**
 * biz_ggec_record 类型 ↔ app 领域类型的唯一翻译点。映射穷尽、无 default 兜底。
 */
public final class RecordMappers {

    private RecordMappers() {
    }

    public static RecordConnectionState state(
            com.yovo.biz_ggec_record.model.RecordConnectionState state) {
        return switch (state) {
            case IDLE -> RecordConnectionState.IDLE;
            case CONNECTING -> RecordConnectionState.CONNECTING;
            case CONNECTED -> RecordConnectionState.CONNECTED;
            case READY -> RecordConnectionState.READY;
            case DISCONNECTED -> RecordConnectionState.DISCONNECTED;
        };
    }

    public static com.yovo.biz_ggec_record.model.RecordVideoCodec codec(RecordVideoCodec codec) {
        return switch (codec) {
            case H264 -> com.yovo.biz_ggec_record.model.RecordVideoCodec.H264;
            case H265 -> com.yovo.biz_ggec_record.model.RecordVideoCodec.H265;
        };
    }

    public static RecordScanFailure failure(
            com.yovo.biz_ggec_record.model.RecordScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> RecordScanFailure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> RecordScanFailure.PERMISSION_DENIED;
            case BLE_UNSUPPORTED -> RecordScanFailure.BLE_UNSUPPORTED;
            case THROTTLED -> RecordScanFailure.THROTTLED;
            case FAILED -> RecordScanFailure.FAILED;
        };
    }

    public static RecordLinkStats stats(com.yovo.biz_ggec_record.model.RecordLinkStats stats) {
        return new RecordLinkStats(stats.videoKbps(), stats.audioKbps(), stats.videoFps());
    }

    public static RecordNearbyDevice device(
            com.yovo.biz_ggec_record.model.RecordNearbyDevice device) {
        return new RecordNearbyDevice(device.macAddress(), device.name(), device.rssi());
    }
}