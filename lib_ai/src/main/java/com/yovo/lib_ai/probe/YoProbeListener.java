package com.yovo.lib_ai.probe;

import androidx.annotation.NonNull;

/**
 * 回调按顺序送到客户端的回调执行器上。每个模型各有一次 {@code onResult}，
 * 最后以一次 {@code onComplete} 收尾，取消时也一样。
 */
public interface YoProbeListener {

    /**
     * 某个模型开始发请求。排队等待并发名额期间不触发。
     */
    default void onStarted(@NonNull String model) {
    }

    default void onResult(@NonNull YoProbeResult result) {
    }

    void onComplete(@NonNull YoProbeSummary summary);
}
