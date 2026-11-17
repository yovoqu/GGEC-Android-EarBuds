package com.ggec.earbuds.data.ai.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiJsonTest {

    private final AiSettingsJson settingsJson = new AiSettingsJson();
    private final AiConversationJson conversationJson = new AiConversationJson();

    @Test
    public void settingsRoundTripWithoutTheKey() {
        AiModel reasoning = new AiModel("deepseek-reasoner", "DeepSeek R", 64_000, 8_000,
                Set.of(AiModelFeature.REASONING, AiModelFeature.VISION), false, null);
        AiSpeedResult ok = new AiSpeedResult("deepseek-chat", AiSpeedResult.Status.OK, 320, 710, null, null, 77, 42);
        AiSpeedResult failed = new AiSpeedResult("deepseek-reasoner", AiSpeedResult.Status.FAILED, -1, 40,
                AiFailureKind.AUTHENTICATION, "bad key", 78, -1);
        AiPlatformConfig platform = new AiPlatformConfig("custom-1a2b3c4d", "openai-compatible", "公司网关",
                "https://gw.example.com", true, AiProtocol.RESPONSES,
                List.of(new AiModel("deepseek-chat", "deepseek-chat", null, null, Set.of(), false, null),
                        reasoning, AiModel.manual("mine")),
                1234L, Map.of("deepseek-chat", ok, "deepseek-reasoner", failed));
        AiSettings settings = new AiSettings(List.of(platform),
                new AiSelection("custom-1a2b3c4d", "mine"),
                new AiVoiceConfig(AiVoiceModel.QWEN, Set.of(AiVoiceModel.QWEN), Map.of()),
                new AiChatPreferences("sys \"quoted\"\nline", false, AiInteractionMode.VOICE));

        String text = settingsJson.encode(settings);
        AiSettings decoded = settingsJson.decode(text);

        assertFalse("keys never reach the settings document", text.contains("sk-secret-value"));
        assertFalse("voice keys never reach the settings document", text.contains("sk-voice-secret"));
        AiPlatformConfig round = decoded.platforms().get(0);
        assertFalse("文档不保存密钥，解出来是未配置", round.configured());
        assertEquals(platform.withoutCredential(), round);
        assertEquals("usage 回来的输出 token 数要跟着测速结果一起持久化", 42,
                round.speedResults().get("deepseek-chat").outputTokens());
        assertEquals(settings.textSelection(), decoded.textSelection());
        assertEquals(settings.voice().model(), decoded.voice().model());
        assertFalse("语音 Key 只走加密存储，文档里没有配置标记", decoded.voice().configured());
        assertEquals(settings.preferences(), decoded.preferences());
        assertEquals(AiInteractionMode.VOICE, decoded.preferences().lastSettingsMode());
        assertEquals(failed, round.speedResults().get("deepseek-reasoner"));
        assertTrue(round.model("mine").orElseThrow().manual());
        assertTrue(round.model("deepseek-reasoner").orElseThrow().features().contains(AiModelFeature.VISION));
    }

    @Test
    public void voiceProviderStateRoundTripsPerProvider() {
        AiVoiceConfig voice = new AiVoiceConfig(AiVoiceModel.CHATGPT,
                Set.of(AiVoiceModel.CHATGPT), Map.of())
                .withBaseUrl("https://gw.example.com/")
                .withRealtimeModels(List.of(
                        new AiModel("gpt-realtime", "gpt-realtime", null, null, Set.of(), false, AiModelCategory.REALTIME),
                        new AiModel("gpt-4o-realtime-preview", "gpt-4o-realtime-preview",
                                null, null, Set.of(), false, AiModelCategory.REALTIME)))
                .withRealtimeModel("gpt-4o-realtime-preview")
                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX);
        AiSettings settings = new AiSettings(List.of(), null, voice, AiChatPreferences.DEFAULT);

        String text = settingsJson.encode(settings);
        AiVoiceConfig decoded = settingsJson.decode(text).voice();

        assertFalse(text.contains("sk-gpt"));
        assertEquals("https://gw.example.com", decoded.baseUrl());
        assertEquals(2, decoded.realtimeModels().size());
        assertEquals("gpt-4o-realtime-preview", decoded.realtimeModelId());
        assertFalse(decoded.configured());
        assertEquals(VoiceDuplex.FULL_DUPLEX, decoded.defaultDuplex());
    }

    @Test
    public void unsupportedVersionIsRejectedBeforeAnyFieldIsRead() {
        DocumentFormatException failure = expectFormat(() -> settingsJson.decode(
                "{\"v\":5,\"preferences\":{},\"voice\":{},\"platforms\":[]}"));
        assertEquals("v", failure.field());
    }

    @Test
    public void aDocumentWithoutVersionIsCorrupt() {
        assertEquals("v", expectFormat(() -> settingsJson.decode(
                "{\"preferences\":{},\"voice\":{},\"platforms\":[]}")).field());
    }

    @Test
    public void missingRequiredFieldsAreCorrupt() {
        assertEquals("lastSettingsMode", expectFormat(() -> settingsJson.decode("""
                {"v":6,
                 "voice":{"model":"chatgpt","defaultDuplex":"half_duplex","providers":{}},
                 "platforms":[],
                 "preferences":{"systemPrompt":"x","showReasoning":true}}
                """)).field());
        assertEquals("templateId", expectFormat(() -> settingsJson.decode("""
                {"v":6,
                 "voice":{"model":"chatgpt","defaultDuplex":"half_duplex","providers":{}},
                 "platforms":[{"id":"p","name":"p","protocol":"chat_completions","models":[],"speed":[],"fetchedAt":0}],
                 "preferences":{"systemPrompt":"x","showReasoning":true,"lastSettingsMode":"TEXT"}}
                """)).field());
    }

    @Test
    public void unknownEnumValuesAreCorruptNotDefaults() {
        assertEquals("platforms.p.protocol", expectFormat(() -> settingsJson.decode("""
                {"v":6,
                 "voice":{"model":"chatgpt","defaultDuplex":"half_duplex","providers":{}},
                 "platforms":[{"id":"p","templateId":"t","name":"p","protocol":"warp_drive",
                   "models":[],"speed":[],"fetchedAt":0}],
                 "preferences":{"systemPrompt":"x","showReasoning":true,"lastSettingsMode":"TEXT"}}
                """)).field());

        assertEquals("voice.model", expectFormat(() -> settingsJson.decode("""
                {"v":6,"platforms":[],
                 "voice":{"model":"some-other-vendor","defaultDuplex":"half_duplex","providers":{}},
                 "preferences":{"systemPrompt":"x","showReasoning":true,"lastSettingsMode":"TEXT"}}
                """)).field());

        assertEquals("preferences.lastSettingsMode", expectFormat(() -> settingsJson.decode("""
                {"v":6,"platforms":[],
                 "voice":{"model":"chatgpt","defaultDuplex":"half_duplex","providers":{}},
                 "preferences":{"systemPrompt":"x","showReasoning":true,"lastSettingsMode":"VIDEO"}}
                """)).field());
    }

    @Test
    public void legacyKeysAreNotAcceptedAnyMore() {
        DocumentFormatException failure = expectFormat(() -> settingsJson.decode("""
                {"v":6,"platforms":[],
                 "voice":{"model":"chatgpt","defaultDuplex":"half_duplex",
                   "providers":{"chatgpt":{"baseUrl":"","realtimeModel":"",
                     "realtimeModels":[{"id":"gpt-realtime","category":"realtime"}]}}},
                 "preferences":{"systemPrompt":"x","showReasoning":true,"lastSettingsMode":"TEXT"}}
                """));
        assertEquals("models", failure.field());
    }

    @Test
    public void documentsThatAreNotObjectsAreRejected() {
        for (String text : new String[] {"", "not json", "[1,2]"}) {
            expectFormat(() -> settingsJson.decode(text));
        }
    }

    @Test
    public void conversationsRoundTripEveryMessageField() {
        AiMessage user = AiMessage.user("u1", "你好\n世界", 100);
        AiMessage done = AiMessage.assistantPlaceholder("a1", "deepseek-chat", 101)
                .appendReasoning("先想").appendText("好的")
                .complete(AiFinishReason.LENGTH, AiUsage.ofTokens(10, 20), 1500);
        AiMessage failed = AiMessage.assistantPlaceholder("a2", null, 102).appendText("半截")
                .fail(AiFailureKind.RATE_LIMITED, 30);
        AiMessage stopped = AiMessage.assistantPlaceholder("a3", "m", 103).appendText("停").stop(9);
        AiConversation conversation = new AiConversation("c1", "标题", 50, 200, List.of(user, done, failed, stopped));

        AiConversation decoded = conversationJson.decode(conversationJson.encode(conversation));

        assertEquals(conversation, decoded);
        assertEquals(AiMessageStatus.ERROR, decoded.messages().get(2).status());
        assertEquals(AiFailureKind.RATE_LIMITED, decoded.messages().get(2).failure().kind());
        assertEquals(30, decoded.messages().get(1).usage().totalTokens());
        assertNull(decoded.messages().get(0).usage());
        assertNull(decoded.messages().get(0).failure());
    }

    @Test
    public void conversationDecodeTreatsAnyBrokenMessageAsCorrupt() {
        assertEquals("v", expectFormat(() -> conversationJson.decode("{\"id\":\"c\",\"messages\":[]}")).field());

        assertEquals("role", expectFormat(() -> conversationJson.decode("""
                {"v":1,"id":"c","title":"","createdAt":1,"updatedAt":9,"messages":[
                  {"id":"m1","role":"BOGUS","text":"ok","status":"COMPLETE","finish":"STOP",
                   "createdAt":1,"elapsed":0}]}
                """)).field());

        assertEquals("status", expectFormat(() -> conversationJson.decode("""
                {"v":1,"id":"c","title":"","createdAt":1,"updatedAt":9,"messages":[
                  {"id":"m1","role":"ASSISTANT","text":"ok","status":"BOGUS","finish":"STOP",
                   "createdAt":1,"elapsed":0}]}
                """)).field());

        assertEquals("messages[0]", expectFormat(() -> conversationJson.decode("""
                {"v":1,"id":"c","title":"","createdAt":1,"updatedAt":9,"messages":[7]}
                """)).field());
    }

    @Test
    public void historyIndexRoundTripsAndRejectsAnUnsupportedVersion() {
        List<AiConversationSummary> summaries = List.of(
                new AiConversationSummary("a", "第一", 300, 4, "预览 \"一\""),
                new AiConversationSummary("b", "", 100, 1, ""));

        assertEquals(summaries, conversationJson.decodeIndex(conversationJson.encodeIndex(summaries)));
        assertEquals("v", expectFormat(() -> conversationJson.decodeIndex(
                "{\"v\":1,\"items\":[]}")).field());
        assertEquals("id", expectFormat(() -> conversationJson.decodeIndex(
                "{\"v\":2,\"items\":[{\"title\":\"no id\"}]}")).field());
    }

    @Test
    public void usageRoundTripKeepsPlatformTotalCostAndCache() {
        AiUsage usage = new AiUsage(120, 30, 0, new BigDecimal("0.00123"), 96, 4);
        AiMessage done = AiMessage.assistantPlaceholder("a1", "gpt-4o", 1)
                .appendText("ok")
                .complete(AiFinishReason.STOP, usage, 900);
        AiConversation conversation = new AiConversation("c", "t", 1, 2, List.of(done));

        AiConversation decoded = conversationJson.decode(conversationJson.encode(conversation));

        assertEquals("新字段必须原样回来", conversation, decoded);
        AiUsage roundTripped = decoded.messages().get(0).usage();
        assertEquals("平台没给合计时按输入加输出", 150, roundTripped.totalTokens());
        assertEquals(Integer.valueOf(96), roundTripped.cachedTokens());
        assertEquals(Integer.valueOf(4), roundTripped.cacheCreationTokens());
        assertEquals(0, new BigDecimal("0.00123").compareTo(roundTripped.cost()));
    }

    private static DocumentFormatException expectFormat(Runnable action) {
        try {
            action.run();
            fail("expected the document to be rejected");
            throw new IllegalStateException("unreachable");
        } catch (DocumentFormatException expected) {
            return expected;
        }
    }
}
