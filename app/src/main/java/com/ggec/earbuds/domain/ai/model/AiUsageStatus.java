package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

/**
 * 一次请求的结局。
 *
 * <p>取消必须和失败分开：取消是用户自己按的停止，不该算进平台错误率；
 * 混在一起会让失败率看起来比实际高好几倍。</p>
 */
public enum AiUsageStatus {
    SUCCESS,
    FAILED,
    CANCELLED;

    /**
     * 宽容解析。认不出来的名字返回 null，由调用方决定丢弃整条记录——
     * 猜成成功会把失败洗白，猜成失败又会凭空造出错误，都不如不记。
     */
    @Nullable
    public static AiUsageStatus parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (AiUsageStatus status : values()) {
            if (status.name().equalsIgnoreCase(name.trim())) {
                return status;
            }
        }
        return null;
    }
}
