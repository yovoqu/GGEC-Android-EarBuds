package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoChatChoice {

    private final int index;
    @Nullable
    private final YoChatMessage message;
    @Nullable
    private final String finishReason;

    public YoChatChoice(int index, @Nullable YoChatMessage message, @Nullable String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    public int index() {
        return index;
    }

    @Nullable
    public YoChatMessage message() {
        return message;
    }

    @Nullable
    public String finishReason() {
        return finishReason;
    }
}
