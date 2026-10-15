package com.yovo.lib_ai.internal.transport;

import androidx.annotation.Nullable;

public final class SseEvent {

    @Nullable
    private final String event;
    private final String data;

    public SseEvent(@Nullable String event, String data) {
        this.event = event;
        this.data = data;
    }

    @Nullable
    public String event() {
        return event;
    }

    public String data() {
        return data;
    }
}
