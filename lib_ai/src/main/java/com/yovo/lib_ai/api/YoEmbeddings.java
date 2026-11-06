package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoEmbeddingRequest;
import com.yovo.lib_ai.retrieval.YoEmbeddingResult;

/**
 * 文本向量化。OpenAI 兼容端点打 {@code POST /v1/embeddings}，
 * DashScope 原生端点打 {@code /api/v1/services/embeddings/text-embedding/text-embedding}。
 *
 * <p>在调用线程上阻塞，不要在主线程调用。一次请求可提交一条或多条文本，
 * 返回的向量按输入顺序排列。</p>
 */
public interface YoEmbeddings {

    /**
     * 把一批文本映射成向量。
     *
     * @throws com.yovo.lib_ai.error.YoAiException 失败时按统一错误分类抛出
     */
    @NonNull
    YoEmbeddingResult embed(@NonNull YoEmbeddingRequest request);
}
