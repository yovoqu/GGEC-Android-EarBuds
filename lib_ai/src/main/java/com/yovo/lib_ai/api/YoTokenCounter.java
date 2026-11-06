package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoTokenCountRequest;
import com.yovo.lib_ai.retrieval.YoTokenCountResult;

/**
 * 调用前估算输入 token。Anthropic {@code POST /v1/messages/count_tokens}，
 * OpenAI {@code POST /v1/responses/input_tokens}。调用在调用线程阻塞。
 */
public interface YoTokenCounter {

    @NonNull
    YoTokenCountResult count(@NonNull YoTokenCountRequest request);
}
