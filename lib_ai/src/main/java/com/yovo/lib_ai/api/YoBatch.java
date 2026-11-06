package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoBatchJob;
import com.yovo.lib_ai.retrieval.YoBatchRequest;
import com.yovo.lib_ai.retrieval.YoBatchResult;

import java.util.List;

/**
 * 批量任务：提交、查询、取消、列出与结果下载。
 *
 * <p>OpenAI / Qwen 兼容打 {@code /v1/batches}；Anthropic 打 {@code /v1/messages/batches}。
 * 状态统一收敛到 {@link com.yovo.lib_ai.retrieval.YoBatchStatus}；
 * 结果按 {@code custom_id} 与输入对齐。调用在调用线程阻塞。</p>
 */
public interface YoBatch {

    /** 提交一个批量任务。 */
    @NonNull
    YoBatchJob submit(@NonNull YoBatchRequest request);

    /** 按 id 查询任务当前状态。 */
    @NonNull
    YoBatchJob retrieve(@NonNull String batchId);

    /** 请求取消一个任务。 */
    @NonNull
    YoBatchJob cancel(@NonNull String batchId);

    /** 列出任务（内部走完分页）。 */
    @NonNull
    List<YoBatchJob> list();

    /** 下载并解析结果，按 {@code custom_id} 对齐。 */
    @NonNull
    List<YoBatchResult> results(@NonNull String batchId);
}
