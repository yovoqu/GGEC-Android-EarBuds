package com.yovo.lib_ai.internal.transport;

import java.io.IOException;
import java.util.function.Consumer;

import okio.BufferedSource;

/**
 * 只切分 SSE 帧。事件名和 data 的含义由各协议解码。
 */
public final class SseDecoder {

    private SseDecoder() {
    }

    public static void read(BufferedSource source, Consumer<SseEvent> consumer) throws IOException {
        String eventName = null;
        StringBuilder data = null;
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                dispatch(eventName, data, consumer);
                return;
            }
            if (line.isEmpty()) {
                dispatch(eventName, data, consumer);
                eventName = null;
                data = null;
                continue;
            }
            if (line.charAt(0) == ':') {
                continue;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = "";
            if (colon >= 0 && colon + 1 < line.length()) {
                value = line.substring(colon + 1);
                if (!value.isEmpty() && value.charAt(0) == ' ') {
                    value = value.substring(1);
                }
            }
            if ("event".equals(field)) {
                eventName = value;
            } else if ("data".equals(field)) {
                if (data == null) {
                    data = new StringBuilder(value);
                } else {
                    data.append('\n').append(value);
                }
            }
        }
    }

    private static void dispatch(String eventName, StringBuilder data, Consumer<SseEvent> consumer) {
        if (data == null) {
            return;
        }
        consumer.accept(new SseEvent(eventName, data.toString()));
    }
}
