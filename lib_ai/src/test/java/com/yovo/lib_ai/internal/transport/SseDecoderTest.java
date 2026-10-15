package com.yovo.lib_ai.internal.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okio.Buffer;

public final class SseDecoderTest {

    @Test
    public void splitsNamedEventsAndJoinsDataLines() throws Exception {
        Buffer buffer = new Buffer();
        buffer.writeUtf8("""
                : heartbeat
                event: message_start
                data: {"a":1}
                data: {"b":2}

                data: [DONE]

                """);

        List<SseEvent> events = new ArrayList<>();
        SseDecoder.read(buffer, events::add);

        assertEquals(2, events.size());
        assertEquals("message_start", events.get(0).event());
        assertEquals("{\"a\":1}\n{\"b\":2}", events.get(0).data());
        assertNull(events.get(1).event());
        assertEquals("[DONE]", events.get(1).data());
    }
}
