package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * lib_ai 提供的三条线协议。{@link #id()} 是稳定的持久化标识，不随枚举名重命名而变。
 */
public enum YoProtocol {
    CHAT_COMPLETIONS("chat_completions"),
    RESPONSES("responses"),
    MESSAGES("messages");

    private final String id;

    YoProtocol(String id) {
        this.id = id;
    }

    @NonNull
    public String id() {
        return id;
    }

    @Nullable
    public static YoProtocol fromId(@Nullable String id) {
        for (YoProtocol protocol : values()) {
            if (protocol.id.equals(id)) {
                return protocol;
            }
        }
        return null;
    }
}
