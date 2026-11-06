package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.media.YoImageRequest;
import com.yovo.lib_ai.media.YoImageResult;

/**
 * 图像生成端口。两种方言：
 * <ul>
 *   <li>Qwen 多模态同步：{@code POST /api/v1/services/aigc/multimodal-generation/generation}；</li>
 *   <li>OpenAI Images：{@code POST /v1/images/generations}。</li>
 * </ul>
 *
 * <p>返回 {@link YoJob} 是为了和视频等真异步任务统一；图像同步方言返回的 job 已是
 * {@link YoJobStatus#SUCCEEDED}。
 */
public interface YoImageGeneration {

    @NonNull
    YoJob<YoImageResult> generate(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoImageRequest request);
}
