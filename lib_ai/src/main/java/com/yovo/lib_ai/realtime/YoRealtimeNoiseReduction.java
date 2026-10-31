package com.yovo.lib_ai.realtime;

/**
 * 输入降噪。近场是手机贴嘴，远场是外放。不配则用服务端默认。
 */
public enum YoRealtimeNoiseReduction {
    NEAR_FIELD("near_field"),
    FAR_FIELD("far_field");

    private final String wireName;

    YoRealtimeNoiseReduction(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
