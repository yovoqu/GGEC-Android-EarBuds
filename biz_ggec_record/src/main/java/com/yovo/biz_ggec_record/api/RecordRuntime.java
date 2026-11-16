package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;

/**
 * 一次 Record 运行时：附近扫描和音视频会话共用同一条 BLE 客户端。
 */
public final class RecordRuntime {

    private final YoRecordNearby nearby;
    private final YoRecordClient client;

    public RecordRuntime(@NonNull YoRecordNearby nearby, @NonNull YoRecordClient client) {
        this.nearby = nearby;
        this.client = client;
    }

    @NonNull
    public YoRecordNearby nearby() {
        return nearby;
    }

    @NonNull
    public YoRecordClient client() {
        return client;
    }

    public void close() {
        nearby.stop();
        client.close();
    }
}
