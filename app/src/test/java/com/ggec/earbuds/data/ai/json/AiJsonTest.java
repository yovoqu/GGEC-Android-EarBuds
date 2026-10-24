package com.ggec.earbuds.data.ai.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.ai.usage.FileAiUsageStore;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;
import com.ggec.earbuds.domain.ai.model.AiUsageSummary;
import com.ggec.earbuds.domain.ai.usecase.RecordUsage;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.json.JSONException;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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
                new AiChatPreferences("sys \"quoted\"\nline", 0.7, 0.9, 2048, 10, false),
                AiVoiceSettings.defaults());

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
                .complete(AiFinishReason.LENGTH, AiUsage.ofTokens(10, 20), 1500);
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

    @Test
    public void usageRoundTripKeepsPlatformTotalCostAndCache() throws Exception {
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

    @Test
    public void legacyUsageNodeWithoutNewFieldsStillDecodes() throws Exception {
        AiConversation decoded = conversationJson.decode("""
                {"id":"c","messages":[
                  {"id":"m","role":"ASSISTANT","text":"ok","usage":{"in":10,"out":5}},
                  {"id":"m2","role":"ASSISTANT","text":"old","usage":{"in":3,"out":1,"cost":0.25}}
                ]}
                """);

        AiUsage legacy = decoded.messages().get(0).usage();
        assertEquals("老文档只有 in/out，total 由 AiUsage 自己求和", 15, legacy.totalTokens());
        assertNull(legacy.cost());
        assertNull(legacy.cachedTokens());
        // 手工数据里成本可能是 JSON 数字，也要读得动。
        assertEquals(0, new BigDecimal("0.25").compareTo(decoded.messages().get(1).usage().cost()));
    }

    @Test
    public void usageStoreAppendsJsonLinesAndSkipsDamagedOnes() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        FileAiUsageStore store = new FileAiUsageStore(documents);
        store.append(AiUsageRecord.request(1000L, "c1", "openai", "gpt-4o",
                AiUsage.ofTokens(10, 5), 800L, AiUsageStatus.SUCCESS, null));
        store.append(AiUsageRecord.request(2000L, null, "anthropic", "claude",
                new AiUsage(7, 3, 0, new BigDecimal("0.02"), 6, 2), 500L,
                AiUsageStatus.FAILED, "rate limited"));

        List<AiUsageRecord> records = store.readAll();

        assertEquals(2, records.size());
        assertEquals(15, records.get(0).totalTokens());
        assertNull(records.get(0).failureReason());
        assertEquals(AiUsageStatus.FAILED, records.get(1).status());
        assertEquals("rate limited", records.get(1).failureReason());
        assertNull("后台请求不属于任何会话", records.get(1).conversationId());
        assertEquals(0, new BigDecimal("0.02").compareTo(records.get(1).cost()));
        assertTrue("行尾要换行，否则下一次追加会粘行",
                documents.documents.get(FileAiUsageStore.DOCUMENT).endsWith("\n"));

        documents.documents.put(FileAiUsageStore.DOCUMENT,
                "{\"ts\":1,\"status\":\"SUCCESS\"}\nnot json\n{\"ts\":2}\n"
                        + "{\"ts\":3,\"status\":\"BOGUS\"}\n");
        assertEquals("坏行只丢自己那一行", 1, store.readAll().size());

        store.clear();
        assertEquals(List.of(), store.readAll());
    }

    @Test
    public void usageSummaryAggregatesByPlatformConversationAndPeriod() {
        long hour = 3_600_000L;
        List<AiUsageRecord> records = List.of(
                AiUsageRecord.request(0L, "c1", "openai", "gpt-4o",
                        AiUsage.ofTokens(10, 5), 100L, AiUsageStatus.SUCCESS, null),
                AiUsageRecord.request(hour + 10L, "c1", "openai", "gpt-4o",
                        new AiUsage(20, 5, 0, new BigDecimal("0.5"), null, null), 200L,
                        AiUsageStatus.FAILED, "boom"),
                AiUsageRecord.request(2 * hour, "c2", "anthropic", "claude",
                        AiUsage.ofTokens(40, 10), 300L, AiUsageStatus.CANCELLED, null),
                AiUsageRecord.request(3 * hour, null, "openai", "gpt-4o",
                        AiUsage.ofTokens(1, 1), 400L, AiUsageStatus.SUCCESS, null));

        AiUsageSummary total = AiUsageSummary.summarize(records);

        assertEquals("失败和取消也各占一次请求", 4, total.requests());
        assertEquals(92, total.totalTokens());
        assertEquals("只有一条报了钱，那就只计这一笔", 1, total.pricedRequests());
        assertEquals(0, new BigDecimal("0.5").compareTo(total.cost()));

        Map<String, AiUsageSummary> byPlatform = AiUsageSummary.byPlatform(records);
        assertEquals(Set.of("anthropic", "openai"), byPlatform.keySet());
        assertEquals(3, byPlatform.get("openai").requests());
        assertEquals(0, new BigDecimal("0.5").compareTo(byPlatform.get("openai").cost()));
        AiUsageSummary anthropic = byPlatform.get("anthropic");
        assertEquals("这条平台从没报过费用：金额 0，但有费用笔数是 0", 0, anthropic.pricedRequests());
        assertFalse(anthropic.hasPricedRequests());
        assertEquals(BigDecimal.ZERO, anthropic.cost());

        Map<String, AiUsageSummary> byConversation = AiUsageSummary.byConversation(records);
        assertEquals(Set.of("c1", "c2"), byConversation.keySet());
        assertEquals(2, byConversation.get("c1").requests());

        NavigableMap<Long, AiUsageSummary> byPeriod = AiUsageSummary.byPeriod(records, hour);
        assertEquals(Set.of(0L, hour, 2 * hour, 3 * hour), byPeriod.keySet());
        assertEquals(15, byPeriod.get(0L).totalTokens());

        AiUsageSummary firstHalf = AiUsageSummary.summarize(records, 0L, 2 * hour);
        assertEquals("左闭右开，相邻区间不重复计数", 2, firstHalf.requests());
    }

    @Test
    public void usageSummaryWithoutReportedCostKeepsPricedRequestsAtZero() {
        AiUsageSummary summary = AiUsageSummary.summarize(List.of(
                AiUsageRecord.request(0L, "c", "p", "m", null, 5L, AiUsageStatus.FAILED, "down")));

        assertEquals("没有用量也要占一行，否则失败率统计不出来", 1, summary.requests());
        assertEquals(0, summary.pricedRequests());
        assertFalse(summary.hasPricedRequests());
        assertEquals(BigDecimal.ZERO, summary.cost());
    }

    @Test
    public void recordUsageSwallowsStoreFailuresButKeepsTheReason() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        FileAiUsageStore store = new FileAiUsageStore(documents);
        RecordUsage recordUsage = new RecordUsage(store, () -> 5_000L);

        recordUsage.success("c1", "openai", "gpt-4o", AiUsage.ofTokens(10, 5), 4_400L);

        AiUsageRecord recorded = store.readAll().get(0);
        assertEquals(AiUsageStatus.SUCCESS, recorded.status());
        assertEquals("耗时由时钟算，不让调用方各减一次", 600L, recorded.elapsedMillis());
        assertNull(recordUsage.lastWriteFailure());

        documents.failWrites = true;
        recordUsage.failed("c1", "openai", "gpt-4o", null, 4_900L, "boom");

        assertNotNull("磁盘满也不能让对话框弹出错误", recordUsage.lastWriteFailure());
        assertTrue(recordUsage.lastWriteFailure().contains("disk is full"));
        assertEquals("失败的那笔没落盘，账还是原来一条", 1, store.readAll().size());

        documents.failWrites = false;
        recordUsage.clearLastWriteFailure();
        recordUsage.cancelled(null, "anthropic", "claude", null, 4_000L);

        assertNull(recordUsage.lastWriteFailure());
        assertEquals(AiUsageStatus.CANCELLED, store.readAll().get(1).status());
    }
}
