package com.yovo.biz_ggec_record.api;

import androidx.annotation.Nullable;

/**
 * Record 设备附近扫描。权限由调用方（app）申请，本接口不弹权限窗。
 */
public interface YoRecordNearby {

    void start(@Nullable RecordNearbyObserver observer);

    void stop();
}
