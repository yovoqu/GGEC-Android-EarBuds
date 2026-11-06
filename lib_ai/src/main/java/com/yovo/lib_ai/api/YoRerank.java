package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoRerankRequest;
import com.yovo.lib_ai.retrieval.YoRerankResult;

/**
 * 候选文档相关性重排。Qwen DashScope {@code POST /api/v1/services/rerank/text-rerank/text-rerank}。
 *
 * <p>在调用线程上阻塞，不要在主线程调用。返回按分数降序。</p>
 */
public interface YoRerank {

    /**
     * 按查询对候选文档打分并排序。
     *
     * @throws com.yovo.lib_ai.error.YoAiException 失败时按统一错误分类抛出
     */
    @NonNull
    YoRerankResult rerank(@NonNull YoRerankRequest request);
}
