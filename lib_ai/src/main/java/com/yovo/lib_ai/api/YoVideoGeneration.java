package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.media.YoVideoRequest;
import com.yovo.lib_ai.media.YoVideoResult;

/**
 * 视频生成端口（Qwen Wan3.0）：{@code POST /api/v1/services/aigc/video-generation/video-synthesis}
 * 带 {@code X-DashScope-Async: enable}，随后轮询 {@code GET /api/v1/tasks/{task_id}} 取
 * {@code output.video_url}。
 */
public interface YoVideoGeneration {

    @NonNull
    YoJob<YoVideoResult> generate(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoVideoRequest request);
}
