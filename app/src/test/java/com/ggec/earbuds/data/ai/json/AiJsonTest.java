package com.ggec.earbuds.data.ai.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsage;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiJsonTest {

    private final AiSettingsJson settingsJson = new AiSettingsJson();
    private final AiConversationJson conversationJson = new AiConversationJson();

    @Test
    public void settingsRoundTripWithoutTheKey() throws Exception {
        AiModel reasoning = new AiModel("deepseek-reasoner", "DeepSeek R", 64_000, 8_000,
                Set.of(AiModelFeature.REASONING, AiModelFeature.VISION), false);
        AiSpeedResult ok = new AiSpeedResult("deepseek-chat", AiSpeedResult.Status.OK, 320, 710, null, null, 77);
        AiSpeedResult failed = new AiSpeedResult("deepseek-reasoner", AiSpeedResult.Status.FAILED, -1, 40,
                AiFailureKind.AUTHENTICATION, "bad key", 78);
        AiPlatformConfig platform = new AiPlatformConfig("custom-1a2b3c4d", "openai-compatible", "公司网关",
                "https://gw.example.com", "sk-secret-value", AiProtocol.RESPONSES,
                List.of(new AiModel("deepseek-chat", "deepseek-chat", null, null, Set.of(), false),
                        reasoning, AiModel.manual("mine")),
                1234L, Map.of("deepseek-chat", ok, "deepseek-reasoner", failed));
        AiSettings settings = new AiSettings(List.of(platform), new AiSelection("custom-1a2b3c4d", "mine"),
                new AiChatPreferences("sys \"quoted\"\nline", 0.7, 2048, 10, false));

        String text = settingsJson.encode(settings);
        AiSettings decoded = settingsJson.decode(text);

        assertFalse("keys never reach the settings document", text.contains("sk-secret-value"));
        AiPlatformConfig round = decoded.platforms().get(0);
        assertEquals("", round.apiKey());
        assertEquals(platform.withoutKey(), round);
        assertEquals(settings.selection(), decoded.selection());
        assertEquals(settings.preferences(), decoded.preferences());
        assertEquals("sys \"quoted\"\nline", decoded.preferences().systemPrompt());
        assertEquals(failed, round.speedResults().get("deepseek-reasoner"));
        assertTrue(round.model("mine").orElseThrow().manual());
        assertTrue(round.model("deepseek-reasoner").orElseThrow().features().contains(AiModelFeature.VISION));
    }

    @Test
    public void settingsDecodeIsForgivingAboutDamagedEntries() throws Exception {
        AiSettings decoded = settingsJson.decode("""
                {"v":1,
                 "selection":{"platformId":"p"},
                 "preferences":{"temperature":9,"contextMessages":-1},
                 "platforms":[
                   {"id":"p","templateId":"deepseek","protocol":"warp_drive",
                    "models":[{"id":"ok"},{"name":"no id"},{"id":" "},5],
                    "speed":[{"modelId":"ok","status":"OK","first":1,"total":2},{"modelId":"ok","status":"???"},{"status":"OK"}]},
                   {"templateId":"deepseek"},
                   "garbage"
                 ]}
                """);

        assertNull("a selection without a model is no selection", decoded.selection());
        assertEquals("invalid preferences fall back to the defaults", AiChatPreferences.DEFAULT, decoded.preferences());
        assertEquals(1, decoded.platforms().size());
        AiPlatformConfig platform = decoded.platforms().get(0);
        assertEquals(AiProtocol.CHAT_COMPLETIONS, platform.protocol());
        assertEquals("p", platform.name());
        assertEquals(List.of("ok"), platform.models().stream().map(AiModel::id).toList());
        assertEquals(1, platform.speedResults().size());
        assertEquals(AiSpeedResult.Status.OK, platform.speedResults().get("ok").status());
    }

    @Test
    public void settingsDecodeRejectsDocumentsThatAreNotObjects() {
        for (String text : new String[] {"", "not json", "[1,2]"}) {
            try {
                settingsJson.decode(text);
                fail("expected a JSON error for: " + text);
            } catch (JSONException expected) {
                // 调用方据此回落到空设置。
            }
        }
    }

    @Test
    public void conversationsRoundTripEveryMessageField() throws Exception {
        AiMessage user = AiMessage.user("u1", "你好\n世界", 100);
        AiMessage done = AiMessage.assistantPlaceholder("a1", "deepseek-chat", 101)
                .appendReasoning("先想").appendText("好的")
                .complete(AiFinishReason.LENGTH, new AiUsage(10, 20), 1500);
        AiMessage failed = AiMessage.assistantPlaceholder("a2", null, 102).appendText("半截")
                .fail(AiFailureKind.RATE_LIMITED, "slow down", 30);
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
    public void conversationDecodeSkipsDamagedMessagesButNeedsAnId() throws Exception {
        AiConversation decoded = conversationJson.decode("""
                {"id":"c","updatedAt":9,"messages":[
                  {"id":"m1","role":"ASSISTANT","text":"ok","status":"BOGUS","finish":"BOGUS"},
                  {"id":"m2"},{"text":"no id"},7]}
                """);

        assertEquals(1, decoded.messages().size());
        assertEquals(AiMessageStatus.COMPLETE, decoded.messages().get(0).status());
        assertEquals(AiFinishReason.STOP, decoded.messages().get(0).finishReason());
        assertEquals(9, decoded.updatedAtMillis());
        assertEquals("", decoded.title());
        try {
            conversationJson.decode("{\"title\":\"x\"}");
            fail("a conversation without an id cannot be stored");
        } catch (JSONException expected) {
            // 没有 id 的文档不可能被找回，直接视为损坏。
        }
    }

    @Test
    public void historyIndexRoundTrips() throws Exception {
        List<AiConversationSummary> summaries = List.of(
                new AiConversationSummary("a", "第一", 300, 4, "预览 \"一\""),
                new AiConversationSummary("b", "", 100, 1, ""));

        assertEquals(summaries, conversationJson.decodeIndex(conversationJson.encodeIndex(summaries)));
        assertEquals(List.of(), conversationJson.decodeIndex("{\"items\":[{\"title\":\"no id\"}]}"));
    }
}
