package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;

/**
 * 发送前由平台执行的 Messages 约束。标准平台不做额外限制。
 */
public interface YoMessagesPolicy {

    void check(@NonNull YoMessageRequest request);

    @NonNull
    static YoMessagesPolicy none() {
        return request -> {
        };
    }
}
