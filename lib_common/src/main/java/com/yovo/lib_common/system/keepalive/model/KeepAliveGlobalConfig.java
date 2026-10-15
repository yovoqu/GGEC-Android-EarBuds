package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class KeepAliveGlobalConfig {

    @NonNull private final KeepAliveLogger logger;

    private KeepAliveGlobalConfig(@NonNull Builder builder) {
        this.logger = builder.logger != null ? builder.logger : KeepAliveLogger.YO_LOG;
    }

    @NonNull
    public KeepAliveLogger logger() {
        return logger;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public static KeepAliveGlobalConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        @Nullable private KeepAliveLogger logger;

        @NonNull
        public Builder logger(@Nullable KeepAliveLogger logger) {
            this.logger = logger;
            return this;
        }

        @NonNull
        public KeepAliveGlobalConfig build() {
            return new KeepAliveGlobalConfig(this);
        }
    }
}
