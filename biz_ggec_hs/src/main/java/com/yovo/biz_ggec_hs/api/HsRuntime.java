package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;

/**
 * 一次 HS 运行时：附近扫描和协议会话共用同一条 BLE 客户端。
 */
public final class HsRuntime {

    private final YoHsNearby nearby;
    private final YoHsClient client;

    public HsRuntime(@NonNull YoHsNearby nearby, @NonNull YoHsClient client) {
        this.nearby = nearby;
        this.client = client;
    }

    @NonNull
    public YoHsNearby nearby() {
        return nearby;
    }

    @NonNull
    public YoHsClient client() {
        return client;
    }

    public void close() {
        nearby.stop();
        client.close();
    }
}
