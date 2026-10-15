package com.yovo.lib_bluetooth.internal.classic.service.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRequest;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtSppUuidPolicy;

import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 通过 {@link com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClient} 暴露的单次服务发现操作配置。
 */
public final class YoCbtServiceDiscoveryConfig {

    private final long timeoutMillis;
    @Nullable
    private final UUID preferredUuid;
    @NonNull
    private final YoCbtSppUuidPolicy uuidPolicy;
    private final boolean allowCacheFallback;
    private final boolean failOnEmpty;
    private final boolean failOnPreferredMismatch;

    private YoCbtServiceDiscoveryConfig(@NonNull Builder builder) {
        this.timeoutMillis = builder.timeoutMillis <= 0 ? 12000L : builder.timeoutMillis;
        this.preferredUuid = builder.preferredUuid;
        this.uuidPolicy = builder.uuidPolicy != null
                ? builder.uuidPolicy
                : YoCbtSppUuidPolicy.standardSpp();
        this.allowCacheFallback = builder.allowCacheFallback;
        this.failOnEmpty = builder.failOnEmpty;
        this.failOnPreferredMismatch = builder.failOnPreferredMismatch;
    }

    public YoCbtServiceDiscoveryConfig(long timeoutMillis) {
        this(builder().timeoutMillis(timeoutMillis));
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
    public YoCbtSdpRequest toRequest(@NonNull YoCbtDeviceId deviceId) {
        return YoCbtSdpRequest.builder(deviceId)
                .timeoutMillis(timeoutMillis)
                .preferredUuid(preferredUuid)
                .uuidPolicy(uuidPolicy)
                .allowCacheFallback(allowCacheFallback)
                .failOnEmpty(failOnEmpty)
                .failOnPreferredMismatch(failOnPreferredMismatch)
                .build();
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeoutMillis = 12000L;
        private UUID preferredUuid;
        private YoCbtSppUuidPolicy uuidPolicy;
        private boolean allowCacheFallback = true;
        private boolean failOnEmpty = true;
        private boolean failOnPreferredMismatch = true;

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder preferredUuid(@Nullable UUID preferredUuid) {
            this.preferredUuid = preferredUuid;
            return this;
        }

        public Builder uuidPolicy(@NonNull YoCbtSppUuidPolicy uuidPolicy) {
            this.uuidPolicy = uuidPolicy;
            return this;
        }

        public Builder allowCacheFallback(boolean allowCacheFallback) {
            this.allowCacheFallback = allowCacheFallback;
            return this;
        }

        public Builder failOnEmpty(boolean failOnEmpty) {
            this.failOnEmpty = failOnEmpty;
            return this;
        }

        public Builder failOnPreferredMismatch(boolean failOnPreferredMismatch) {
            this.failOnPreferredMismatch = failOnPreferredMismatch;
            return this;
        }

        @NonNull
        public YoCbtServiceDiscoveryConfig build() {
            return new YoCbtServiceDiscoveryConfig(this);
        }
    }
}
