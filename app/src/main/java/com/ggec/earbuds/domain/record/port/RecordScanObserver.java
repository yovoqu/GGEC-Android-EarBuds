package com.ggec.earbuds.domain.record.port;

import com.ggec.earbuds.domain.record.model.RecordNearbyDevice;
import com.ggec.earbuds.domain.record.model.RecordScanFailure;

import java.util.List;

/**
 * Record 附近设备扫描回调（领域侧）。
 */
public interface RecordScanObserver {

    default void onDevicesChanged(List<RecordNearbyDevice> devices) {
    }

    default void onScanFailed(RecordScanFailure failure) {
    }
}
