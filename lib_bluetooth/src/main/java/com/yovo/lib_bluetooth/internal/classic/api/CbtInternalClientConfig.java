package com.yovo.lib_bluetooth.internal.classic.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeConfig;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link CbtInternalClient} 全局配置，包含 Hidden API 开关、SPP 写队列容量与运行时参数。
 */
public final class CbtInternalClientConfig {
    private final boolean allowHiddenApiBondManagement;
    private final boolean allowHiddenApiSppChannel;
    private final int sppWriteQueueCapacity;
    private final YoCbtRuntimeConfig runtimeConfig;

    private CbtInternalClientConfig(Builder builder) {
        this.allowHiddenApiBondManagement = builder.allowHiddenApiBondManagement;
        this.allowHiddenApiSppChannel = builder.allowHiddenApiSppChannel;
        this.sppWriteQueueCapacity = builder.sppWriteQueueCapacity <= 0 ? 256 : builder.sppWriteQueueCapacity;
        this.runtimeConfig = builder.runtimeConfig != null
                ? builder.runtimeConfig
                : YoCbtRuntimeConfig.builder().build();
    }

    public boolean isAllowHiddenApiBondManagement() {
        return allowHiddenApiBondManagement;
    }

    public boolean isAllowHiddenApiSppChannel() {
        return allowHiddenApiSppChannel;
    }

    public int getSppWriteQueueCapacity() {
        return sppWriteQueueCapacity;
    }

    @NonNull
    public YoCbtRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean allowHiddenApiBondManagement;
        private boolean allowHiddenApiSppChannel;
        private int sppWriteQueueCapacity = 256;
        private YoCbtRuntimeConfig runtimeConfig;

        public Builder allowHiddenApiBondManagement(boolean allowHiddenApiBondManagement) {
            this.allowHiddenApiBondManagement = allowHiddenApiBondManagement;
            return this;
        }

        public Builder allowHiddenApiSppChannel(boolean allowHiddenApiSppChannel) {
            this.allowHiddenApiSppChannel = allowHiddenApiSppChannel;
            return this;
        }

        public Builder sppWriteQueueCapacity(int sppWriteQueueCapacity) {
            this.sppWriteQueueCapacity = sppWriteQueueCapacity;
            return this;
        }

        public Builder runtimeConfig(@NonNull YoCbtRuntimeConfig runtimeConfig) {
            this.runtimeConfig = runtimeConfig;
            return this;
        }

        public CbtInternalClientConfig build() {
            return new CbtInternalClientConfig(this);
        }
    }
}
