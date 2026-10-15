package com.yovo.lib_bluetooth.internal.classic.service.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtSppUuidPolicy;

import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 描述单次 SDP 发现操作的不可变请求。
 */
public final class YoCbtSdpRequest {

    @NonNull
    private final YoCbtDeviceId deviceId;
    private final long timeoutMillis;
    @Nullable
    private final UUID preferredUuid;
    @NonNull
    private final YoCbtSppUuidPolicy uuidPolicy;
    private final boolean allowCacheFallback;
    private final boolean failOnEmpty;
    private final boolean failOnPreferredMismatch;

    private YoCbtSdpRequest(@NonNull Builder builder) {
        this.deviceId = builder.deviceId;
        this.timeoutMillis = builder.timeoutMillis <= 0 ? 12000L : builder.timeoutMillis;
        this.preferredUuid = builder.preferredUuid;
        this.uuidPolicy = builder.uuidPolicy != null
                ? builder.uuidPolicy
                : YoCbtSppUuidPolicy.standardSpp();
        this.allowCacheFallback = builder.allowCacheFallback;
        this.failOnEmpty = builder.failOnEmpty;
        this.failOnPreferredMismatch = builder.failOnPreferredMismatch;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Nullable
    public UUID getPreferredUuid() {
        return preferredUuid;
    }

    @NonNull
    public YoCbtSppUuidPolicy getUuidPolicy() {
        return uuidPolicy;
    }

    public boolean isAllowCacheFallback() {
        return allowCacheFallback;
    }

    public boolean isFailOnEmpty() {
        return failOnEmpty;
    }

    public boolean isFailOnPreferredMismatch() {
        return failOnPreferredMismatch;
    }

    @NonNull
    public static Builder builder(@NonNull YoCbtDeviceId deviceId) {
        return new Builder(deviceId);
    }

    public static final class Builder {
        private final YoCbtDeviceId deviceId;
        private long timeoutMillis = 12000L;
        private UUID preferredUuid;
        private YoCbtSppUuidPolicy uuidPolicy;
        private boolean allowCacheFallback = true;
        private boolean failOnEmpty = true;
        private boolean failOnPreferredMismatch = true;

        public Builder(@NonNull YoCbtDeviceId deviceId) {
            this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        }

        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        @NonNull
        public Builder preferredUuid(@Nullable UUID preferredUuid) {
            this.preferredUuid = preferredUuid;
            return this;
        }

        @NonNull
        public Builder uuidPolicy(@NonNull YoCbtSppUuidPolicy uuidPolicy) {
            this.uuidPolicy = uuidPolicy;
            return this;
        }

        @NonNull
        public Builder allowCacheFallback(boolean allowCacheFallback) {
            this.allowCacheFallback = allowCacheFallback;
            return this;
        }

        @NonNull
        public Builder failOnEmpty(boolean failOnEmpty) {
            this.failOnEmpty = failOnEmpty;
            return this;
        }

        @NonNull
        public Builder failOnPreferredMismatch(boolean failOnPreferredMismatch) {
            this.failOnPreferredMismatch = failOnPreferredMismatch;
            return this;
        }

        @NonNull
        public YoCbtSdpRequest build() {
            return new YoCbtSdpRequest(this);
        }
    }
}
