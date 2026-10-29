package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;

import java.util.List;
import java.util.Set;

/**
 * 路由：能力 → 服务 → 端点。
 */
public final class YoCapabilityRouterTest {

    private static final String OPENAI = "https://api.openai.com";

    @Test
    public void chatVisionRoutesToTheUserDeclaredChatProtocol() {
        // 视觉能力不再由内置厂商表声明；用户勾过之后才可信。
        YoModelCatalog catalog = YoModelCatalog.standard()
                .withUserCapabilities("gpt-4o",
                        Set.of(YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION));
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI, catalog);
        YoModelRoute route = router.route(YoModelCapability.CHAT_VISION,
                Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("gpt-4o"));

        assertEquals(YoModelCapability.CHAT_VISION, route.capability());
        assertEquals("gpt-4o", route.model().id());
        assertEquals(YoServiceKind.CHAT_COMPLETIONS, route.serviceKind());
        assertEquals(YoProtocol.CHAT_COMPLETIONS, route.protocol());
        assertEquals(OPENAI + "/v1/chat/completions", route.endpoint());
        assertTrue(route.conversational());
        assertEquals(YoCapabilitySource.USER, route.model().source());
    }

    @Test
    public void onlyTheDeclaredChatProtocolIsUsed() {
        YoModelRoute route = new DefaultCapabilityRouter("https://api.anthropic.com")
                .route(YoModelCapability.CHAT_TEXT, Set.of(YoServiceKind.MESSAGES),
                        List.of("claude-3-5-sonnet-20241022"));
        assertEquals(YoProtocol.MESSAGES, route.protocol());
        assertEquals("https://api.anthropic.com/v1/messages", route.endpoint());

        YoModelRoute responses = new DefaultCapabilityRouter("https://api.openai.com")
                .routeWithProtocols(YoModelCapability.CHAT_TEXT,
                        List.of(YoProtocol.RESPONSES), List.of("gpt-4.1"));
        assertEquals(YoProtocol.RESPONSES, responses.protocol());
        assertEquals("https://api.openai.com/v1/responses", responses.endpoint());

        // 两条都声明时按 Chat Completions → Responses 的偏好取
        YoModelRoute preferred = new DefaultCapabilityRouter("https://api.openai.com")
                .routeWithProtocols(YoModelCapability.CHAT_TEXT,
                        List.of(YoProtocol.RESPONSES, YoProtocol.CHAT_COMPLETIONS), List.of("gpt-4.1"));
        assertEquals(YoProtocol.CHAT_COMPLETIONS, preferred.protocol());
    }

    @Test
    public void transcriptionAndSpeechPickTheirOwnService() {
        YoModelRoute transcription = new DefaultCapabilityRouter(OPENAI)
                .route(YoModelCapability.TRANSCRIPTION, Set.of(YoServiceKind.AUDIO_TRANSCRIBE),
                        List.of("gpt-4o", "whisper-1"));
        assertEquals("whisper-1", transcription.model().id());
        assertEquals(YoServiceKind.AUDIO_TRANSCRIBE, transcription.serviceKind());
        assertNull(transcription.protocol());
        assertEquals(OPENAI + "/v1/audio/transcriptions", transcription.endpoint());

        YoModelRoute speech = new DefaultCapabilityRouter(OPENAI)
                .route(YoModelCapability.SPEECH_SYNTHESIS, Set.of(YoServiceKind.AUDIO_SPEECH),
                        List.of("tts-1"));
        assertEquals(YoServiceKind.AUDIO_SPEECH, speech.serviceKind());
        assertEquals(OPENAI + "/v1/audio/speech", speech.endpoint());
    }

    @Test
    public void realtimeUsesTheWebSocketEndpoint() {
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI)
                .route(YoModelCapability.REALTIME_AUDIO, Set.of(YoServiceKind.REALTIME),
                        List.of("gpt-4o-realtime-preview"));
        assertEquals(YoServiceKind.REALTIME, route.serviceKind());
        assertNull(route.protocol());
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview", route.endpoint());
    }

    @Test
    public void missingServiceFailsWithCapabilityAndModelList() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> router.route(YoModelCapability.CHAT_TEXT,
                        Set.of(YoServiceKind.AUDIO_TRANSCRIBE), List.of("whisper-1")));

        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, YoAiErrors.kindOf(failure));
        assertEquals(YoModelCapability.CHAT_TEXT, failure.capability());
        String message = failure.getMessage();
        assertTrue(message, message.contains("CHAT_TEXT"));
        assertTrue(message, message.contains("whisper-1"));
        assertTrue(message, message.contains("AUDIO_TRANSCRIBE"));
    }

    @Test
    public void serviceWithoutACapableModelAlsoFails() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> router.route(YoModelCapability.CHAT_TEXT,
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("whisper-1")));
        assertTrue(failure.getMessage(), failure.getMessage().contains("whisper-1"));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
    }

    @Test
    public void embeddingIsNotRoutedThisRound() {
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> new DefaultCapabilityRouter(OPENAI).route(YoModelCapability.EMBEDDING,
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("text-embedding-3-small")));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
        assertTrue(failure.getMessage(), failure.getMessage().contains("EMBEDDING"));
    }

    @Test
    public void userDeclaredModelsWinOverInferredOnes() {
        // 两个 id 都只能推断成 CHAT_TEXT；用户给 acme-vision-pro 勾了视觉，才轮得到它。
        YoModelCatalog catalog = YoModelCatalog.standard()
                .withUserCapabilities("acme-vision-pro",
                        Set.of(YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION));
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI, catalog)
                .route(YoModelCapability.CHAT_VISION, Set.of(YoServiceKind.CHAT_COMPLETIONS),
                        List.of("acme-vision-pro", "gpt-4o"));
        assertEquals("acme-vision-pro", route.model().id());
        assertEquals(YoCapabilitySource.USER, route.model().source());
    }

    @Test
    public void inferredModelIsUsedWhenNothingElseFits() {
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI)
                .route(YoModelCapability.TRANSCRIPTION, Set.of(YoServiceKind.AUDIO_TRANSCRIBE),
                        List.of("acme-whisper-large"));
        assertEquals("acme-whisper-large", route.model().id());
        assertEquals(YoCapabilitySource.INFERRED, route.model().source());
        assertEquals(OPENAI + "/v1/audio/transcriptions", route.endpoint());
    }

    @Test
    public void preferredModelIsHonouredWhenItSupportsTheCapability() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoModelRoute preferred = router.route(YoModelCapability.CHAT_TEXT,
                Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("gpt-4o", "gpt-4o-mini"), "gpt-4o-mini");
        assertEquals("gpt-4o-mini", preferred.model().id());

        // 不支持就回落到平台的顺序
        YoModelRoute fellBack = router.route(YoModelCapability.CHAT_TEXT,
                Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("gpt-4o"), "whisper-1");
        assertEquals("gpt-4o", fellBack.model().id());
        assertFalse(fellBack.model().id().equals("whisper-1"));
    }
}
