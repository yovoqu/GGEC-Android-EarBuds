package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

/**
 * 发送前由平台执行的 Chat 请求约束。标准平台不做额外限制。
 */
public interface YoChatRequestPolicy {

    void check(@NonNull YoChatCompletionRequest request);

    @NonNull
    static YoChatRequestPolicy none() {
        return request -> {
        };
    }
}
