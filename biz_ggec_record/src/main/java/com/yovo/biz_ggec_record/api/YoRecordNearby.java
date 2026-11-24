package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Record 设备附近扫描。权限由调用方（app）申请，本接口不弹权限窗。
 */
public interface YoRecordNearby {

    /**
     * 下发机型筛选。空集表示不限（本目录仅 Ambiq 时效果相同）。
     * 切换筛选不强制重启扫描；未命中筛选的设备不会上报。
     */
    void filterModels(@NonNull Set<String> modelIds);

    void start(@Nullable RecordNearbyObserver observer);

    void stop();

    boolean isScanning();
}
