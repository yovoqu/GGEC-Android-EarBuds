package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次视频生成结果。{@code videoUrl} 通常 24h 过期，调用方应尽快下载落盘。
 */
public final class YoVideoResult {

    @Nullable
    private final String videoUrl;
    @Nullable
    private final String taskId;
    @Nullable
    private final String requestId;
    @Nullable
    private final String status;
    @Nullable
    private final String rawJson;

    public YoVideoResult(
            @Nullable String videoUrl,
            @Nullable String taskId,
            @Nullable String requestId,
            @Nullable String status,
            @Nullable String rawJson) {
        this.videoUrl = videoUrl;
        this.taskId = taskId;
        this.requestId = requestId;
        this.status = status;
        this.rawJson = rawJson;
    }

    @Nullable
    public String videoUrl() {
        return videoUrl;
    }

    @Nullable
    public String taskId() {
        return taskId;
    }

    @Nullable
    public String requestId() {
        return requestId;
    }

    /** 服务端任务状态文本，如 {@code SUCCEEDED}。 */
    @Nullable
    public String status() {
        return status;
    }

    @Nullable
    public String rawJson() {
        return rawJson;
    }
}
