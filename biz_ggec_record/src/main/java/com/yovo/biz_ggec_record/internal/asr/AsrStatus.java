package com.yovo.biz_ggec_record.internal.asr;

/**
 * 识别器生命周期与模型加载状态。
 */
public enum AsrStatus {
    IDLE,
    LOADING,
    READY,
    FINISHED
}
