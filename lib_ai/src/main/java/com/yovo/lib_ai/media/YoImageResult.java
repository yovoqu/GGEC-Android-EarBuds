package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 一次图像生成结果。图片可能以 URL 或 Base64 返回（OpenAI {@code response_format=url|b64_json}），
 * 两种都保留，调用方按需取；不在库里下载或解码图片。
 */
public final class YoImageResult {

    @NonNull
    private final List<String> urls;
    @NonNull
    private final List<String> base64;
    @NonNull
    private final List<String> revisedPrompts;
    @Nullable
    private final String taskId;
    @Nullable
    private final String requestId;
    @Nullable
    private final String rawJson;

    public YoImageResult(
            @NonNull List<String> urls,
            @NonNull List<String> base64,
            @NonNull List<String> revisedPrompts,
            @Nullable String taskId,
            @Nullable String requestId,
            @Nullable String rawJson) {
        this.urls = List.copyOf(urls);
        this.base64 = List.copyOf(base64);
        this.revisedPrompts = List.copyOf(revisedPrompts);
        this.taskId = taskId;
        this.requestId = requestId;
        this.rawJson = rawJson;
    }

    @NonNull
    public List<String> urls() {
        return urls;
    }

    @NonNull
    public List<String> base64() {
        return base64;
    }

    @NonNull
    public List<String> revisedPrompts() {
        return revisedPrompts;
    }

    /** 第一张图片的 URL；只有 Base64 形态时为 {@code null}。 */
    @Nullable
    public String firstUrl() {
        return urls.isEmpty() ? null : urls.get(0);
    }

    /** 实际拿到的图片数量（URL 与 Base64 两个列表的较大者）。 */
    public int imageCount() {
        return Math.max(urls.size(), base64.size());
    }

    @Nullable
    public String taskId() {
        return taskId;
    }

    @Nullable
    public String requestId() {
        return requestId;
    }

    @Nullable
    public String rawJson() {
        return rawJson;
    }
}
