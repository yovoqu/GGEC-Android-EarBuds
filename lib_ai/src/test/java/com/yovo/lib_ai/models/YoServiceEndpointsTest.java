package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 端点拼装：{@code /v1} 不重复补，完整端点原样返回，实时语音走 wss。
 */
public final class YoServiceEndpointsTest {

    private static final String V1 = "/v1";

    @Test
    public void versionSegmentIsNeverDuplicated() {
        String[] bases = {
                "https://api.openai.com",
                "https://api.openai.com/",
                "  https://api.openai.com//  ",
                "https://api.openai.com/v1",
                "https://api.openai.com/v1/"
        };
        for (String base : bases) {
            assertEquals(base, "https://api.openai.com/v1/chat/completions",
                    YoServiceEndpoints.chatCompletions(base));
            assertEquals(base, "https://api.openai.com/v1/messages", YoServiceEndpoints.messages(base));
            assertEquals(base, "https://api.openai.com/v1/responses", YoServiceEndpoints.responses(base));
            assertEquals(base, "https://api.openai.com/v1/audio/transcriptions",
                    YoServiceEndpoints.transcriptions(base));
            assertEquals(base, "https://api.openai.com/v1/audio/speech", YoServiceEndpoints.speech(base));
            assertFalse(base, YoServiceEndpoints.chatCompletions(base).contains(V1 + V1));
        }
    }

    @Test
    public void gatewayPrefixIsPreserved() {
        assertEquals("https://gw.example.com/openai/v1/audio/transcriptions",
                YoServiceEndpoints.transcriptions("https://gw.example.com/openai/v1"));
        assertEquals("https://gw.example.com/openai/v1/chat/completions",
                YoServiceEndpoints.chatCompletions("https://gw.example.com/openai/"));
    }

    @Test
    public void alreadyCompleteEndpointIsReturnedAsIs() {
        assertEquals("https://api.openai.com/v1/audio/transcriptions",
                YoServiceEndpoints.transcriptions("https://api.openai.com/v1/audio/transcriptions"));
        assertEquals("https://api.openai.com/v1/chat/completions",
                YoServiceEndpoints.chatCompletions("https://api.openai.com/v1/chat/completions"));
    }

    @Test
    public void realtimeUsesWebSocketAndCarriesTheModel() {
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview",
                YoServiceEndpoints.realtime("https://api.openai.com", "gpt-4o-realtime-preview"));
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview",
                YoServiceEndpoints.realtime("https://api.openai.com/v1", "gpt-4o-realtime-preview"));
        assertEquals("ws://127.0.0.1:8080/v1/realtime?model=qwen2.5%3A7b",
                YoServiceEndpoints.realtime("http://127.0.0.1:8080", "qwen2.5:7b"));

        assertThrows(IllegalArgumentException.class,
                () -> YoServiceEndpoints.realtime("https://api.openai.com", " "));
    }

    @Test
    public void endpointDispatchesOnTheServiceKind() {
        String baseUrl = "https://api.openai.com/v1";
        assertEquals("https://api.openai.com/v1/chat/completions",
                YoServiceEndpoints.endpoint(baseUrl, YoServiceKind.CHAT_COMPLETIONS, null));
        assertEquals("https://api.openai.com/v1/messages",
                YoServiceEndpoints.endpoint(baseUrl, YoServiceKind.MESSAGES, null));
        assertEquals("https://api.openai.com/v1/responses",
                YoServiceEndpoints.endpoint(baseUrl, YoServiceKind.RESPONSES, null));
        assertEquals("https://api.openai.com/v1/audio/transcriptions",
                YoServiceEndpoints.endpoint(baseUrl, YoServiceKind.AUDIO_TRANSCRIBE, "whisper-1"));
        assertEquals("https://api.openai.com/v1/audio/speech",
                YoServiceEndpoints.endpoint(baseUrl, YoServiceKind.AUDIO_SPEECH, "tts-1"));
        assertTrue(YoServiceEndpoints
                .endpoint(baseUrl, YoServiceKind.REALTIME, "gpt-4o-realtime-preview")
                .startsWith("wss://"));
    }

    @Test
    public void protocolMappingCoversTheTwoWorlds() {
        assertEquals(com.yovo.lib_ai.api.YoProtocol.CHAT_COMPLETIONS,
                YoServiceKind.CHAT_COMPLETIONS.asProtocol());
        assertEquals(com.yovo.lib_ai.api.YoProtocol.MESSAGES, YoServiceKind.MESSAGES.asProtocol());
        assertEquals(com.yovo.lib_ai.api.YoProtocol.RESPONSES, YoServiceKind.RESPONSES.asProtocol());
        org.junit.Assert.assertNull(YoServiceKind.AUDIO_TRANSCRIBE.asProtocol());
        org.junit.Assert.assertNull(YoServiceKind.AUDIO_SPEECH.asProtocol());
        org.junit.Assert.assertNull(YoServiceKind.REALTIME.asProtocol());
        assertTrue(YoServiceKind.CHAT_COMPLETIONS.conversational());
        assertFalse(YoServiceKind.REALTIME.conversational());
    }
}
