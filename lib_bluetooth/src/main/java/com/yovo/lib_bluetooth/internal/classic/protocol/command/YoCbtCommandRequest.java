package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import com.yovo.lib_bluetooth.internal.classic.protocol.matcher.YoCbtResponseMatcher;
import com.yovo.lib_bluetooth.internal.classic.protocol.retry.YoCbtRetryPolicy;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令请求，包含待发送数据包、匹配器与回调。
 */

public final class YoCbtCommandRequest {
    private final String commandId;
    private final YoCbtCommand command;
    private final long timeoutMillis;
    private final YoCbtRetryPolicy retryPolicy;
    private final YoCbtResponseMatcher responseMatcher;

    private YoCbtCommandRequest(Builder builder) {
        this.commandId = builder.commandId;
        this.command = builder.command;
        this.timeoutMillis = Math.max(1L, builder.timeoutMillis);
        this.retryPolicy = builder.retryPolicy;
        this.responseMatcher = builder.responseMatcher;
    }

    @NonNull
    public static Builder builder(@NonNull String commandId, @NonNull YoCbtCommand command) {
        return new Builder(commandId, command);
    }

    @NonNull
    public String getCommandId() {
        return commandId;
    }

    @NonNull
    public YoCbtCommand getCommand() {
        return command;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @NonNull
    public YoCbtRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    @NonNull
    public YoCbtResponseMatcher getResponseMatcher() {
        return responseMatcher;
    }

    public static final class Builder {
        private final String commandId;
        private final YoCbtCommand command;
        private long timeoutMillis = 3000L;
        private YoCbtRetryPolicy retryPolicy = YoCbtRetryPolicy.none();
        private YoCbtResponseMatcher responseMatcher = YoCbtResponseMatcher.FIRST_PENDING;

        private Builder(String commandId, YoCbtCommand command) {
            this.commandId = commandId;
            this.command = command;
        }

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder retryPolicy(YoCbtRetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder responseMatcher(YoCbtResponseMatcher responseMatcher) {
            this.responseMatcher = responseMatcher;
            return this;
        }

        public YoCbtCommandRequest build() {
            return new YoCbtCommandRequest(this);
        }
    }
}
