package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;

import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * 路由：要求 → 服务 → 模型 → 端点。
 */
public final class YoCapabilityRouterTest {

    private static final String OPENAI = "https://api.openai.com";

    /** 平台上报过"这是对话模型，能看图"的模型。 */
    private static YoModelSpec reportedVision(String id) {
        return YoModelSpec.builder(id)
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
    }

    private static YoModelSpec reportedText(String id) {
        return YoModelSpec.builder(id).category(YoModelCategory.TEXT, YoSpecSource.REPORTED).build();
    }

    @Test
    public void visionRoutesToTheModelThePlatformReportedAsSeeing() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoModelRoute route = router.route(YoModelRequirement.vision(),
                Set.of(YoServiceKind.CHAT_COMPLETIONS),
                List.of(reportedText("gpt-4o-mini"), reportedVision("gpt-4o")));

        assertEquals("gpt-4o", route.model().id());
        assertEquals(YoModelCategory.VISION, route.model().category());
        assertEquals(YoModelFit.CONFIRMED, route.fit());
        assertTrue(route.confirmed());
        assertEquals(YoServiceKind.CHAT_COMPLETIONS, route.serviceKind());
        assertEquals(YoProtocol.CHAT_COMPLETIONS, route.protocol());
        assertEquals(OPENAI + "/v1/chat/completions", route.endpoint());
        assertTrue(route.conversational());
        assertEquals(YoModelRequirement.vision(), route.requirement());
    }

    @Test
    public void visionRoutesToAUserDeclaredModelByIdOnly() {
        // 只有 id 时视觉能力无从推断；用户在设置里确认之后才轮得到它。
        YoModelCatalog catalog = YoModelCatalog.standard().withOverride("gpt-4o",
                YoModelOverride.builder().category(YoModelCategory.VISION).build());
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI, catalog);

        YoModelRoute route = router.routeByIds(YoModelRequirement.vision(),
                Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("gpt-4o"), null);
        assertEquals("gpt-4o", route.model().id());
        assertEquals(YoSpecSource.USER, route.model().categorySource());
        assertEquals(YoModelFit.CONFIRMED, route.fit());

        YoCapabilityException unknown = assertThrows(YoCapabilityException.class,
                () -> new DefaultCapabilityRouter(OPENAI).routeByIds(YoModelRequirement.vision(),
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("gpt-4o"), null));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, unknown.kind());
    }

    @Test
    public void onlyTheDeclaredChatProtocolIsUsed() {
        YoModelRoute route = new DefaultCapabilityRouter("https://api.anthropic.com")
                .routeByIds(YoModelRequirement.text(), Set.of(YoServiceKind.MESSAGES),
                        List.of("claude-3-5-sonnet-20241022"), null);
        assertEquals(YoProtocol.MESSAGES, route.protocol());
        assertEquals("https://api.anthropic.com/v1/messages", route.endpoint());

        YoModelRoute responses = new DefaultCapabilityRouter("https://api.openai.com")
                .routeWithProtocols(YoModelRequirement.text(),
                        List.of(YoProtocol.RESPONSES), List.of(reportedText("gpt-4.1")));
        assertEquals(YoProtocol.RESPONSES, responses.protocol());
        assertEquals("https://api.openai.com/v1/responses", responses.endpoint());

        // 两条都声明时按 Chat Completions → Responses → Messages 的偏好取
        YoModelRoute preferred = new DefaultCapabilityRouter("https://api.openai.com")
                .routeWithProtocols(YoModelRequirement.text(),
                        List.of(YoProtocol.RESPONSES, YoProtocol.CHAT_COMPLETIONS),
                        List.of(reportedText("gpt-4.1")));
        assertEquals(YoProtocol.CHAT_COMPLETIONS, preferred.protocol());
    }

    @Test
    public void speechServicesPickTheirOwnEndpoints() {
        YoModelRoute transcription = new DefaultCapabilityRouter(OPENAI)
                .routeByIds(YoModelRequirement.speechRecognition(), Set.of(YoServiceKind.AUDIO_TRANSCRIBE),
                        List.of("gpt-4o", "whisper-1"), null);
        assertEquals("whisper-1", transcription.model().id());
        assertEquals(YoServiceKind.AUDIO_TRANSCRIBE, transcription.serviceKind());
        assertNull(transcription.protocol());
        assertEquals(OPENAI + "/v1/audio/transcriptions", transcription.endpoint());

        YoModelRoute speech = new DefaultCapabilityRouter(OPENAI)
                .routeByIds(YoModelRequirement.speechSynthesis(), Set.of(YoServiceKind.AUDIO_SPEECH),
                        List.of("tts-1"), null);
        assertEquals(YoServiceKind.AUDIO_SPEECH, speech.serviceKind());
        assertEquals(OPENAI + "/v1/audio/speech", speech.endpoint());
    }

    @Test
    public void realtimeUsesTheWebSocketEndpoint() {
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI)
                .routeByIds(YoModelRequirement.realtime(), Set.of(YoServiceKind.REALTIME),
                        List.of("gpt-4o-realtime-preview"), null);
        assertEquals(YoServiceKind.REALTIME, route.serviceKind());
        assertNull(route.protocol());
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview", route.endpoint());
    }

    @Test
    public void embeddingsRouteOnceThePlatformDeclaresTheService() {
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI)
                .routeByIds(YoModelRequirement.embedding(), Set.of(YoServiceKind.EMBEDDINGS),
                        List.of("gpt-4o", "text-embedding-3-small"), null);
        assertEquals("text-embedding-3-small", route.model().id());
        assertEquals(YoServiceKind.EMBEDDINGS, route.serviceKind());
        assertNull(route.protocol());
        assertFalse(route.conversational());
        assertEquals(OPENAI + "/v1/embeddings", route.endpoint());
        assertEquals("向量模型 id 是推断的，只能是待确认", YoModelFit.INFERRED, route.fit());
        assertFalse(route.confirmed());
    }

    @Test
    public void missingServiceFailsWithRequirementAndModelList() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> router.routeByIds(YoModelRequirement.text(),
                        Set.of(YoServiceKind.AUDIO_TRANSCRIBE), List.of("whisper-1"), null));

        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, YoAiErrors.kindOf(failure));
        assertEquals(YoModelRequirement.text(), failure.requirement());
        String message = failure.getMessage();
        assertTrue(message, message.contains("文本模型"));
        assertTrue(message, message.contains("TEXT"));
        assertTrue(message, message.contains("whisper-1"));
        assertTrue(message, message.contains("AUDIO_TRANSCRIBE"));
    }

    @Test
    public void embeddingWithoutADeclaredServiceFailsClearly() {
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> new DefaultCapabilityRouter(OPENAI).routeByIds(YoModelRequirement.embedding(),
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("text-embedding-3-small"), null));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
        assertTrue(failure.getMessage(), failure.getMessage().contains("EMBEDDING"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("CHAT_COMPLETIONS"));
    }

    @Test
    public void categoriesTheSdkHasNoServiceForFailEvenWhenEverythingIsDeclared() {
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> new DefaultCapabilityRouter(OPENAI).routeByIds(
                        YoModelRequirement.of(YoModelCategory.IMAGE_GENERATION),
                        Set.of(YoServiceKind.values()), List.of("dall-e-3"), null));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
        assertTrue(failure.getMessage(), failure.getMessage().contains("暂时没有"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("图像生成"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("IMAGE_GENERATION"));
    }

    @Test
    public void serviceWithoutACapableModelAlsoFails() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        YoCapabilityException failure = assertThrows(YoCapabilityException.class,
                () -> router.routeByIds(YoModelRequirement.text(),
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of("whisper-1"), null));
        assertTrue(failure.getMessage(), failure.getMessage().contains("whisper-1"));
        assertEquals(YoAiErrorKind.UNSUPPORTED_CAPABILITY, failure.kind());
    }

    @Test
    public void confirmedModelsBeatEarlierInferredOnes() {
        // acme-plain 排在前面，但它只是"按名字推断出来的文本模型"；平台上报过的 acme-reported 先用。
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI).route(YoModelRequirement.text(),
                Set.of(YoServiceKind.CHAT_COMPLETIONS),
                List.of(YoModelInference.infer("acme-plain"), reportedText("acme-reported")));
        assertEquals("acme-reported", route.model().id());
        assertEquals(YoModelFit.CONFIRMED, route.fit());
    }

    @Test
    public void inferredModelIsUsedWhenNothingElseFits() {
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI)
                .routeByIds(YoModelRequirement.speechRecognition(), Set.of(YoServiceKind.AUDIO_TRANSCRIBE),
                        List.of("acme-whisper-large"), null);
        assertEquals("acme-whisper-large", route.model().id());
        assertEquals(YoModelFit.INFERRED, route.fit());
        assertEquals(YoSpecSource.INFERRED, route.model().categorySource());
        assertEquals(OPENAI + "/v1/audio/transcriptions", route.endpoint());
    }

    @Test
    public void serverCapabilitiesSelectTheModel() {
        YoModelSpec withTools = YoModelSpec.builder("acme-tools")
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        YoModelRoute route = new DefaultCapabilityRouter(OPENAI).route(
                YoModelRequirement.text().withCapability(YoModelCapability.FUNCTION_CALLING),
                Set.of(YoServiceKind.CHAT_COMPLETIONS),
                List.of(reportedText("acme-plain"), withTools));
        assertEquals("acme-tools", route.model().id());

        YoCapabilityException none = assertThrows(YoCapabilityException.class,
                () -> new DefaultCapabilityRouter(OPENAI).route(
                        YoModelRequirement.text().withCapability(YoModelCapability.WEB_SEARCH),
                        Set.of(YoServiceKind.CHAT_COMPLETIONS), List.of(reportedText("acme-plain"), withTools)));
        assertTrue(none.getMessage(), none.getMessage().contains("联网搜索"));
    }

    @Test
    public void omniModelsServeTextAndVisionRequests() {
        YoModelSpec omni = YoModelSpec.builder("acme-omni")
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        Set<YoServiceKind> chat = Set.of(YoServiceKind.CHAT_COMPLETIONS);

        assertEquals("acme-omni", router.route(YoModelRequirement.text(), chat, List.of(omni)).model().id());
        assertEquals("acme-omni", router.route(YoModelRequirement.vision(), chat, List.of(omni)).model().id());
        assertEquals("acme-omni", router.route(YoModelRequirement.omni(), chat, List.of(omni)).model().id());
        assertThrows(YoCapabilityException.class,
                () -> router.route(YoModelRequirement.realtime(), Set.of(YoServiceKind.REALTIME), List.of(omni)));
    }

    @Test
    public void preferredModelIsHonouredWhenItFits() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        Set<YoServiceKind> chat = Set.of(YoServiceKind.CHAT_COMPLETIONS);
        YoModelRoute preferred = router.routeByIds(YoModelRequirement.text(), chat,
                List.of("gpt-4o", "gpt-4o-mini"), "gpt-4o-mini");
        assertEquals("gpt-4o-mini", preferred.model().id());

        // 不满足就回落到平台的顺序
        YoModelRoute fellBack = router.routeByIds(YoModelRequirement.text(), chat,
                List.of("gpt-4o"), "whisper-1");
        assertEquals("gpt-4o", fellBack.model().id());

        // 手动添加的模型不在平台列表里，也照样用
        YoModelRoute manual = router.routeByIds(YoModelRequirement.text(), chat,
                List.of("gpt-4o"), "my-company-model");
        assertEquals("my-company-model", manual.model().id());
    }

    @Test
    public void preferredModelUsesTheListedSpecWhenThePlatformHasIt() {
        YoCapabilityRouter router = new DefaultCapabilityRouter(OPENAI);
        Set<YoServiceKind> chat = Set.of(YoServiceKind.CHAT_COMPLETIONS);
        YoModelRoute route = router.route(YoModelRequirement.vision(), chat,
                List.of(reportedText("a-text"), reportedVision("B-Vision")), "b-vision");
        assertEquals("忽略大小写命中平台列表里带上报的那一份", "B-Vision", route.model().id());
        assertEquals(YoModelFit.CONFIRMED, route.fit());
    }

    @Test
    public void routesRefuseUnfitModelsAndBlankEndpoints() {
        YoModelSpec spec = reportedText("acme");
        assertThrows(IllegalArgumentException.class, () -> new YoModelRoute(
                YoModelRequirement.text(), spec, YoModelFit.REJECTED, YoServiceKind.CHAT_COMPLETIONS,
                OPENAI + "/v1/chat/completions"));
        assertThrows(IllegalArgumentException.class, () -> new YoModelRoute(
                YoModelRequirement.text(), spec, YoModelFit.CONFIRMED, YoServiceKind.CHAT_COMPLETIONS, " "));
    }

    @Test
    public void dedicatedServicesFollowTheCategory() {
        assertEquals(YoServiceKind.AUDIO_TRANSCRIBE, YoServiceKind.dedicatedFor(YoModelCategory.SPEECH_RECOGNITION));
        assertEquals(YoServiceKind.AUDIO_SPEECH, YoServiceKind.dedicatedFor(YoModelCategory.SPEECH_SYNTHESIS));
        assertEquals(YoServiceKind.EMBEDDINGS, YoServiceKind.dedicatedFor(YoModelCategory.EMBEDDING));
        assertEquals(YoServiceKind.REALTIME, YoServiceKind.dedicatedFor(YoModelCategory.REALTIME));
        for (YoModelCategory chat : new YoModelCategory[] {
                YoModelCategory.TEXT, YoModelCategory.VISION, YoModelCategory.OMNI}) {
            assertNull("对话类走哪条协议由平台声明决定：" + chat, YoServiceKind.dedicatedFor(chat));
        }
        assertNull(YoServiceKind.dedicatedFor(YoModelCategory.IMAGE_GENERATION));
    }
}
