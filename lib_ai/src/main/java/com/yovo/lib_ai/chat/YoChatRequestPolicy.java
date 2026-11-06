package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 发送前由平台执行的 Chat 请求约束。标准平台不做额外限制。
 */
public interface YoChatRequestPolicy {

    void check(@NonNull YoChatCompletionRequest request);

    /**
     * 对已经编码好的请求体做方言改写：补默认值、删禁用字段、改字段名。
     * 默认什么都不做。
     */
    default void apply(@NonNull JSONObject body) throws JSONException {
    }

    @NonNull
    static YoChatRequestPolicy none() {
        return request -> {
        };
    }
}
