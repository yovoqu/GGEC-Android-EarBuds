package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.model.RecordNearbyDevice;
import com.yovo.biz_ggec_record.model.RecordScanFailure;

import java.util.List;

/**
 * 附近设备扫描回调。默认空实现；回调在主线程。
 */
public interface RecordNearbyObserver {

    default void onDevicesChanged(@NonNull List<RecordNearbyDevice> devices) {
    }

    default void onScanFailed(@NonNull RecordScanFailure failure) {
    }
}
