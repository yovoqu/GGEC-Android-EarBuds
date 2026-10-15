package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.responses.YoResponseInput;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamListener;

import java.util.List;

/**
 * 一条到 {@code /v1/responses} 的 WebSocket。
 * 同一 {@code streamId} 在当前响应结束前不能再 {@link #create}。
 * {@code response.incomplete} 且原因为 {@code steered} 时通道保持占用，后续事件仍交给同一个监听器。
 */
public interface YoResponsesConnection extends AutoCloseable {

    /**
     * @param streamId 为空表示默认通道。非空时只能包含字母、数字、下划线、连字符和点，长度 1 到 256。
     */
    @NonNull
    YoAiCall create(
            @Nullable String streamId,
            @NonNull YoResponseRequest request,
            @NonNull YoResponseStreamListener listener);

    void steer(@NonNull String previousResponseId, @NonNull String inputText);

    void steer(@NonNull String previousResponseId, @NonNull List<YoResponseInput> inputItems);

    @Override
    void close();
}
