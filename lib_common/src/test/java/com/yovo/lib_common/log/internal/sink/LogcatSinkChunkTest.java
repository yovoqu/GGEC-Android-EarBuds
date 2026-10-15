package com.yovo.lib_common.log.internal.sink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LogcatSinkChunkTest {

    @Test
    public void shortMessage_singleChunkWithoutPrefix() {
        List<String> chunks = LogcatSink.chunk("short");

        assertEquals(1, chunks.size());
        assertEquals("short", chunks.get(0));
    }

    @Test
    public void longMessage_splitWithIndexPrefix() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 9000; i++) {
            builder.append('x');
        }

        List<String> chunks = LogcatSink.chunk(builder.toString());

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).startsWith("(1/3) "));
        assertTrue(chunks.get(1).startsWith("(2/3) "));
        assertTrue(chunks.get(2).startsWith("(3/3) "));
    }
}
