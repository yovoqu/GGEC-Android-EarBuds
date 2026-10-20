package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

public interface AiSpeedTestListener {

    default void onStarted(String modelId) {
    }

    default void onResult(AiSpeedResult result) {
    }

    /**
     * 无论正常结束还是被取消，都恰好回调一次。
     */
    void onComplete(boolean cancelled);
}
