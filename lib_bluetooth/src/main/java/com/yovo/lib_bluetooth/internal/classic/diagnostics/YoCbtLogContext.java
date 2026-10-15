package com.yovo.lib_bluetooth.internal.classic.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description YoCbt 模块日志上下文，封装连接 ID、设备 ID、traceId 等字段，不可变。
 */
public final class YoCbtLogContext {

    private final String connectionId;
    private final String deviceId;
    private final String traceId;
    private final String operationName;

    private YoCbtLogContext(Builder builder) {
        this.connectionId = builder.connectionId;
        this.deviceId = builder.deviceId;
        this.traceId = builder.traceId;
        this.operationName = builder.operationName;
    }

    // ── Getter ───────────────────────────────────────────────────

    @Nullable
    public String getConnectionId() {
        return connectionId;
    }

    @Nullable
    public String getDeviceId() {
        return deviceId;
    }

    @Nullable
    public String getTraceId() {
        return traceId;
    }

    @Nullable
    public String getOperationName() {
        return operationName;
    }

    /**
     * 返回适合拼接到日志消息前的格式化前缀字符串。
     * 格式：{@code [connId][deviceId][traceId:opName]}，仅包含非 null 字段。
     */
    @NonNull
    public String toLogPrefix() {
        StringBuilder sb = new StringBuilder();
        if (connectionId != null) sb.append('[').append(connectionId).append(']');
        if (deviceId != null) sb.append('[').append(deviceId).append(']');
        if (traceId != null) {
            sb.append('[').append(traceId);
            if (operationName != null) sb.append(':').append(operationName);
            sb.append(']');
        }
        return sb.toString();
    }

    // ── Builder ───────────────────────────────────────────────────

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String connectionId;
        private String deviceId;
        private String traceId;
        private String operationName;

        private Builder() {
        }

        @NonNull
        public Builder connectionId(@Nullable String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        @NonNull
        public Builder deviceId(@Nullable String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        @NonNull
        public Builder traceId(@Nullable String traceId) {
            this.traceId = traceId;
            return this;
        }

        @NonNull
        public Builder operationName(@Nullable String operationName) {
            this.operationName = operationName;
            return this;
        }

        @NonNull
        public YoCbtLogContext build() {
            return new YoCbtLogContext(this);
        }
    }

    // ── Object ────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtLogContext)) return false;
        YoCbtLogContext that = (YoCbtLogContext) o;
        return Objects.equals(connectionId, that.connectionId)
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(operationName, that.operationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, deviceId, traceId, operationName);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoCbtLogContext{"
                + "logPrefix='" + toLogPrefix() + '\''
                + '}';
    }
}
