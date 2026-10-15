package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoResponseStreamEvent {

    private final String type;
    @Nullable
    private final String streamId;
    @Nullable
    private final String delta;
    private final int outputIndex;
    private final String rawJson;

    public YoResponseStreamEvent(
            @NonNull String type,
            @Nullable String streamId,
            @Nullable String delta,
            int outputIndex,
            @NonNull String rawJson) {
        this.type = type;
        this.streamId = streamId;
        this.delta = delta;
        this.outputIndex = outputIndex;
        this.rawJson = rawJson;
    }

    @NonNull
    public String type() {
        return type;
    }

    @Nullable
    public String streamId() {
        return streamId;
    }

    @Nullable
    public String delta() {
        return delta;
    }

    public int outputIndex() {
        return outputIndex;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
