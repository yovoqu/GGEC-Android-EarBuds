package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次异步任务（图像/视频生成等）。
 *
 * <p>同步方言（OpenAI Images、Qwen 多模态同步）返回的 job 直接是 {@link YoJobStatus#SUCCEEDED}；
 * 真异步方言（Qwen {@code video-synthesis}）在 {@code generate} 内完成轮询，超时折成
 * {@link YoJobStatus#FAILED} 并给出可读原因。结果 URL 通常 24h 过期，调用方应尽快落盘。
 */
public interface YoJob<T> {

    /** 服务端任务 id；同步完成或对端未给时为 {@code null}。 */
    @Nullable
    String id();

    @NonNull
    YoJobStatus status();

    /** 成功时的结果；未成功时为 {@code null}。 */
    @Nullable
    T result();

    /** 失败原因（可直接展示）；非失败态为 {@code null}。 */
    @Nullable
    String failureMessage();

    /** 是否已到终态（成功/失败/取消）。 */
    boolean isDone();

    /** 请求取消。已经到终态时是空操作。 */
    void cancel();
}
