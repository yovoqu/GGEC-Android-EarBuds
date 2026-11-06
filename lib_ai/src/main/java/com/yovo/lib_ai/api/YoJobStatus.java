package com.yovo.lib_ai.api;

/**
 * 异步任务状态机。图像/视频这类"先提交、后轮询"的接口统一用这一套。
 *
 * <pre>
 * QUEUED → RUNNING → SUCCEEDED
 *                 ↘ FAILED
 * 任意非终态 → CANCELLED（调用方主动取消）
 * </pre>
 */
public enum YoJobStatus {
    /** 已提交，未开始处理。 */
    QUEUED,
    /** 处理中，需要继续轮询。 */
    RUNNING,
    /** 成功，{@code result()} 可用。 */
    SUCCEEDED,
    /** 失败，{@code failureMessage()} 给出可读原因。 */
    FAILED,
    /** 调用方取消。 */
    CANCELLED
}
