package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.messages.YoMessage;
import com.yovo.lib_ai.messages.YoMessageRequest;
import com.yovo.lib_ai.messages.YoMessageStreamListener;

/**
 * Anthropic {@code POST /v1/messages}。
 */
public interface YoAnthropicMessages {

    @NonNull
    YoMessage create(@NonNull YoMessageRequest request);

    @NonNull
    YoAiCall stream(@NonNull YoMessageRequest request, @NonNull YoMessageStreamListener listener);
}
