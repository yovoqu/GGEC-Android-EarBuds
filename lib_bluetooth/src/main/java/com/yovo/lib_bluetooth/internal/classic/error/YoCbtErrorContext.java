package com.yovo.lib_bluetooth.internal.classic.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.error.YoErrorCode;
import com.yovo.lib_bluetooth.internal.common.error.YoErrorContext;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 错误上下文，携带设备、连接 ID 等诊断字段。
 */

public final class YoCbtErrorContext {
    private final YoErrorContext base;
    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileType profileType;
    private final YoCbtSppConnectionId connectionId;
    private final YoCbtSppEndpoint endpoint;

    private YoCbtErrorContext(Builder builder) {
        this.base = builder.baseBuilder.build();
        this.deviceId = builder.deviceId;
        this.profileType = builder.profileType;
        this.connectionId = builder.connectionId;
        this.endpoint = builder.endpoint;
    }

    @NonNull public YoErrorContext getBase() { return base; }
    @Nullable public YoCbtDeviceId getDeviceId() { return deviceId; }
    @Nullable public YoCbtProfileType getProfileType() { return profileType; }
    @Nullable public YoCbtSppConnectionId getConnectionId() { return connectionId; }
    @Nullable public YoCbtSppEndpoint getEndpoint() { return endpoint; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final YoErrorContext.Builder baseBuilder = YoErrorContext.builder();
        private YoCbtDeviceId deviceId;
        private YoCbtProfileType profileType;
        private YoCbtSppConnectionId connectionId;
        private YoCbtSppEndpoint endpoint;

        public Builder from(YoErrorContext base) {
            baseBuilder.errorCode(base.errorCode()).message(base.message())
                    .cause(base.cause()).recoverable(base.recoverable())
                    .retryable(base.retryable()).sourceModule(base.sourceModule())
                    .traceId(base.traceId()).timestamp(base.timestamp());
            return this;
        }

        public Builder errorCode(YoErrorCode c) { baseBuilder.errorCode(c); return this; }
        public Builder message(String m) { baseBuilder.message(m); return this; }
        public Builder cause(Throwable t) { baseBuilder.cause(t); return this; }
        public Builder recoverable(boolean r) { baseBuilder.recoverable(r); return this; }
        public Builder retryable(boolean r) { baseBuilder.retryable(r); return this; }
        public Builder sourceModule(String m) { baseBuilder.sourceModule(m); return this; }
        public Builder traceId(String t) { baseBuilder.traceId(t); return this; }
        public Builder deviceId(YoCbtDeviceId d) { this.deviceId = d; return this; }
        public Builder profileType(YoCbtProfileType p) { this.profileType = p; return this; }
        public Builder connectionId(YoCbtSppConnectionId c) { this.connectionId = c; return this; }
        public Builder endpoint(YoCbtSppEndpoint e) { this.endpoint = e; return this; }

        public YoCbtErrorContext build() { return new YoCbtErrorContext(this); }
    }
}
