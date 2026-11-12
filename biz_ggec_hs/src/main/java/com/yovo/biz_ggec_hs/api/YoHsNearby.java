package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;

/**
 * HS 附近设备扫描。实现使用 BLE 扫描，并只把协议认出来的耳机交出来。
 */
public interface YoHsNearby {

    void start(@NonNull HsNearbyObserver observer);

    void stop();

    boolean isScanning();
}
