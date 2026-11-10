package com.yovo.biz_ggec_hs.model.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 各代际广播共有的设备身份。代际专有字段留在具体类型上，不在这里填空值。
 */
public interface HsBroadcastIdentity {
    @NonNull
    HsGeneration generation();

    @Nullable
    String bleAddress();

    @NonNull
    String classicAddress();

    int marketId();

    @NonNull
    String color();

    boolean headset();
}
