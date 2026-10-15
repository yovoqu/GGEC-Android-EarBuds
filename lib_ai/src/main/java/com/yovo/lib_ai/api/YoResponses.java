package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamListener;

/**
 * OpenAI {@code POST /v1/responses}，以及同一协议的 WebSocket 连接。
 */
public interface YoResponses {

    @NonNull
    YoResponseObject create(@NonNull YoResponseRequest request);

    @NonNull
    YoAiCall stream(@NonNull YoResponseRequest request, @NonNull YoResponseStreamListener listener);

    @NonNull
    YoResponsesConnection connect();
}
