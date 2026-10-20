package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;

import java.util.Set;

/**
 * 不关心协议的对话补全：同一份请求可以走 Chat Completions、Responses 或 Messages，
 * 流式增量和最终结果归一成同一组类型，思考内容单独成流。
 * 一次性调用在调用线程上阻塞，不要在主线程调用。
 */
public interface YoCompletions {

    /**
     * 该平台实际提供的协议。
     */
    @NonNull
    Set<YoProtocol> protocols();

    /**
     * 请求没有指定协议时使用的协议：优先 Chat Completions，其次 Messages，最后 Responses。
     */
    @NonNull
    YoProtocol defaultProtocol();

    @NonNull
    YoCompletionResult complete(@NonNull YoCompletionRequest request);

    @NonNull
    YoAiCall stream(@NonNull YoCompletionRequest request, @NonNull YoCompletionListener listener);
}
