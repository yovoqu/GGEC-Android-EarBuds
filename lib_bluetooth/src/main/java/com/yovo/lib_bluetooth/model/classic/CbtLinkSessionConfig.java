package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 链路会话配置：就绪判定的时间与策略参数。
 *
 * <p>默认值面向「耳机类设备」：先主动查询一次既有链路（命中即就绪，不发任何请求），
 * 未命中才发起音频连接并等待状态事件。</p>
 */
public final class CbtLinkSessionConfig {

    /** 默认就绪等待超时：15 秒。 */
    public static final long DEFAULT_READINESS_TIMEOUT_MILLIS = 15_000L;

    /** 默认查询重试间隔：链路可能正在建立，定期复核既有事实。 */
    public static final long DEFAULT_QUERY_RETRY_MILLIS = 2_000L;

    private final long readinessTimeoutMillis;
    private final long queryRetryMillis;
    private final boolean connectAudioWhenDown;
    private final boolean createBondWhenUnbonded;

    private CbtLinkSessionConfig(Builder builder) {
        this.readinessTimeoutMillis = builder.readinessTimeoutMillis > 0
                ? builder.readinessTimeoutMillis
                : DEFAULT_READINESS_TIMEOUT_MILLIS;
        this.queryRetryMillis = builder.queryRetryMillis > 0
                ? builder.queryRetryMillis
                : DEFAULT_QUERY_RETRY_MILLIS;
        this.connectAudioWhenDown = builder.connectAudioWhenDown;
        this.createBondWhenUnbonded = builder.createBondWhenUnbonded;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public static CbtLinkSessionConfig defaults() {
        return builder().build();
    }

    public long readinessTimeoutMillis() {
        return readinessTimeoutMillis;
    }

    public long queryRetryMillis() {
        return queryRetryMillis;
    }

    /** 判定为 {@link CbtLinkReadiness#DOWN} 时是否发起 connectAudio。 */
    public boolean connectAudioWhenDown() {
        return connectAudioWhenDown;
    }

    /** 设备未配对时是否先发起配对。 */
    public boolean createBondWhenUnbonded() {
        return createBondWhenUnbonded;
    }

    public static final class Builder {
        private long readinessTimeoutMillis = DEFAULT_READINESS_TIMEOUT_MILLIS;
        private long queryRetryMillis = DEFAULT_QUERY_RETRY_MILLIS;
        private boolean connectAudioWhenDown = true;
        private boolean createBondWhenUnbonded = true;

        public Builder readinessTimeoutMillis(long value) {
            this.readinessTimeoutMillis = value;
            return this;
        }

        public Builder queryRetryMillis(long value) {
            this.queryRetryMillis = value;
            return this;
        }

        public Builder connectAudioWhenDown(boolean value) {
            this.connectAudioWhenDown = value;
            return this;
        }

        public Builder createBondWhenUnbonded(boolean value) {
            this.createBondWhenUnbonded = value;
            return this;
        }

        @NonNull
        public CbtLinkSessionConfig build() {
            return new CbtLinkSessionConfig(this);
        }
    }
}
