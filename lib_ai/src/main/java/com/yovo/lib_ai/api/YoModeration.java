package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoModerationRequest;
import com.yovo.lib_ai.retrieval.YoModerationResult;

/**
 * 输入/输出内容审核。OpenAI {@code POST /v1/moderations}；Qwen 可选走
 * {@code X-DashScope-DataInspection}。调用在调用线程阻塞。
 */
public interface YoModeration {

    @NonNull
    YoModerationResult moderate(@NonNull YoModerationRequest request);
}
