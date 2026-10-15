package com.yovo.lib_bluetooth.model.classic;

/**
 * 经典蓝牙客户端全局配置。
 */
public final class CbtConfig {

    private final boolean allowHiddenApiBondManagement;
    private final boolean allowHiddenApiSppChannel;
    private final int sppWriteQueueCapacity;

    private CbtConfig(Builder builder) {
        this.allowHiddenApiBondManagement = builder.allowHiddenApiBondManagement;
        this.allowHiddenApiSppChannel = builder.allowHiddenApiSppChannel;
        this.sppWriteQueueCapacity = builder.sppWriteQueueCapacity <= 0 ? 256 : builder.sppWriteQueueCapacity;
    }

    public boolean allowHiddenApiBondManagement() {
        return allowHiddenApiBondManagement;
    }

    public boolean allowHiddenApiSppChannel() {
        return allowHiddenApiSppChannel;
    }

    public int sppWriteQueueCapacity() {
        return sppWriteQueueCapacity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean allowHiddenApiBondManagement;
        private boolean allowHiddenApiSppChannel;
        private int sppWriteQueueCapacity = 256;

        public Builder allowHiddenApiBondManagement(boolean value) {
            this.allowHiddenApiBondManagement = value;
            return this;
        }

        public Builder allowHiddenApiSppChannel(boolean value) {
            this.allowHiddenApiSppChannel = value;
            return this;
        }

        public Builder sppWriteQueueCapacity(int value) {
            this.sppWriteQueueCapacity = value;
            return this;
        }

        public CbtConfig build() {
            return new CbtConfig(this);
        }
    }
}
