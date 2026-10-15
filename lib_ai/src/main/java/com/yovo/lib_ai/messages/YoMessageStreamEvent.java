package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoMessageStreamEvent {

    private final String type;
    private final int index;
    @Nullable
    private final String delta;
    private final String rawJson;

    public YoMessageStreamEvent(@NonNull String type, int index, @Nullable String delta, @NonNull String rawJson) {
        this.type = type;
        this.index = index;
        this.delta = delta;
        this.rawJson = rawJson;
    }

    @NonNull
    public String type() {
        return type;
    }

    public int index() {
        return index;
    }

    @Nullable
    public String delta() {
        return delta;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
