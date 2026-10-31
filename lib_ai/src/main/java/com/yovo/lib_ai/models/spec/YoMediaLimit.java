package com.yovo.lib_ai.models.spec;

import androidx.annotation.Nullable;

/**
 * 某一种输入模态的体量上限。每一项都可能没人说过，没说过就是 {@code null}，不猜。
 *
 * <p>用在 {@link YoModelLimits#media(YoModality)}：图片一次最多几张、每张多大；
 * 音频、视频最长几秒；文本一次最多几段（向量模型的批量上限）。</p>
 *
 * @param maxItems   一次请求最多放几个这种内容
 * @param maxBytes   单个内容的字节上限
 * @param maxSeconds 单个内容的时长上限（音频、视频）
 */
public record YoMediaLimit(
        @Nullable Integer maxItems,
        @Nullable Long maxBytes,
        @Nullable Integer maxSeconds) {

    /** 什么都没说过。 */
    public static final YoMediaLimit NONE = new YoMediaLimit(null, null, null);

    public YoMediaLimit {
        maxItems = maxItems != null && maxItems > 0 ? maxItems : null;
        maxBytes = maxBytes != null && maxBytes > 0 ? maxBytes : null;
        maxSeconds = maxSeconds != null && maxSeconds > 0 ? maxSeconds : null;
    }

    public boolean isEmpty() {
        return maxItems == null && maxBytes == null && maxSeconds == null;
    }

    /**
     * {@code later} 里说过的覆盖这里的，没说的保留。
     */
    public YoMediaLimit overlay(@Nullable YoMediaLimit later) {
        if (later == null) {
            return this;
        }
        return new YoMediaLimit(
                later.maxItems != null ? later.maxItems : maxItems,
                later.maxBytes != null ? later.maxBytes : maxBytes,
                later.maxSeconds != null ? later.maxSeconds : maxSeconds);
    }
}
