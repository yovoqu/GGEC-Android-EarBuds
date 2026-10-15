package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙扫描操作配置，包含超时与是否要求设备名称等参数。
 */
public final class YoCbtDiscoveryConfig {
    private final long timeoutMillis;
    private final boolean requireName;

    private YoCbtDiscoveryConfig(Builder builder) {
        timeoutMillis = builder.timeoutMillis <= 0 ? 12000L : builder.timeoutMillis;
        requireName = builder.requireName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public boolean isRequireName() {
        return requireName;
    }

    @NonNull
    public YoCbtDiscoveryFilter toFilter() {
        return YoCbtDiscoveryFilter.builder().requireName(requireName).build();
    }

    public static final class Builder {
        private long timeoutMillis;
        private boolean requireName;

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder requireName(boolean requireName) {
            this.requireName = requireName;
            return this;
        }

        public YoCbtDiscoveryConfig build() {
            return new YoCbtDiscoveryConfig(this);
        }
    }
}
