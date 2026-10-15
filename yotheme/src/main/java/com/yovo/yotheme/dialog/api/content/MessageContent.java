package com.yovo.yotheme.dialog.api.content;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 纯文本消息主体内容
 */
public final class MessageContent extends DialogContentSpec {

    @NonNull
    private final String message;

    public MessageContent(@NonNull String message) {
        this.message = message;
    }

    @NonNull
    public String getMessage() {
        return message;
    }
}

