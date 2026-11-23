package com.ggec.earbuds.domain.record.model;

/**
 * Record 会话连接状态（APP 呈现词汇，与 biz {@code RecordConnectionState} 由 provider 一一映射）。
 */
public enum RecordConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    READY,
    DISCONNECTED
}
