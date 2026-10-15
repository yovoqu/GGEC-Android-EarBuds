package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatStreamListener;

/**
 * OpenAI {@code POST /v1/chat/completions}。
 * {@link #complete} 在调用线程上阻塞，不要在主线程调用。
 */
public interface YoChatCompletions {

    @NonNull
    YoChatCompletion complete(@NonNull YoChatCompletionRequest request);

    @NonNull
    YoAiCall stream(@NonNull YoChatCompletionRequest request, @NonNull YoChatStreamListener listener);
}
