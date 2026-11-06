package com.yovo.lib_ai.retrieval;

/**
 * 批量任务的规范化状态。把 OpenAI 的 {@code status} 与 Anthropic 的 {@code processing_status}
 * 收敛到同一套生命周期，供轮询方判断终态。
 */
public enum YoBatchStatus {
    /** 服务端正在校验输入。 */
    VALIDATING,
    /** 处理中。 */
    IN_PROGRESS,
    /** 正在收尾 / 生成结果文件。 */
    FINALIZING,
    /** 全部完成。 */
    COMPLETED,
    /** 任务失败。 */
    FAILED,
    /** 即将过期。 */
    EXPIRING,
    /** 已过期。 */
    EXPIRED,
    /** 正在取消。 */
    CANCELLING,
    /** 已取消。 */
    CANCELLED,
    /** 认不出的状态。 */
    UNKNOWN;

    /** 是否终态，轮询可停。 */
    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED || this == CANCELLED;
    }
}
