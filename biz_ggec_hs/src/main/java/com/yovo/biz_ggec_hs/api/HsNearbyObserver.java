package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.HsScanFailure;

import java.util.List;

/** 附近耳机扫描回调。每帧广播都会更新已识别设备。 */
public interface HsNearbyObserver {

    void onUpdated(@NonNull List<HsNearbyDevice> devices);

    default void onLost(@NonNull HsNearbyDevice device) {
    }

    void onFailed(@NonNull HsScanFailure failure);

    void onFinished();
}
