package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 平台详情页的显示规则。这些判断以前散在 Activity 里，现在全在这里，可逐条验。 */
public final class AiPlatformUiMapperTest {

    private final AiPlatformUiMapper mapper = new AiPlatformUiMapper();

    // ---- 连接 ----

    @Test
    public void withoutKeyTheTestIsBlockedAndSaysWhy() {
        AiPlatformConfig config = config("", List.of(model("chat", null)));

        AiPlatformUiState.Connection connection = mapper.map(
                settings(config, null), config, null, Set.of(), false, false, "").connection();

        assertEquals("先填 API Key", connection.blockedReason());
        assertNull(connection.result());
    }

    @Test
    public void withoutSelectionTheTestIsBlockedOnModel() {
        AiPlatformConfig config = config("sk-abc", List.of(model("chat", null)));

        AiPlatformUiState.Connection connection = mapper.map(
                settings(config, null), config, null, Set.of(), false, false, "").connection();

        assertEquals("先选一个模型", connection.blockedReason());
    }

    @Test
    public void aSuccessfulProbeBecomesTheConnectionResult() {
        AiPlatformConfig config = config("sk-abc", List.of(model("chat", null)))
                .withSpeedResult(new AiSpeedResult("chat", AiSpeedResult.Status.OK,
                        829L, 1500L, null, null, 1L));

        AiPlatformUiState.Connection connection = mapper.map(
                settings(config, "chat"), config, null, Set.of(), false, false, "").connection();

        assertNull(connection.blockedReason());
        assertEquals("连接正常 · 首字 829 ms", connection.result());
    }

    // ---- 模型行 ----

    @Test
    public void factsPutLatencyBeforeContext() {
        AiModel model = model("chat", 128_000);
        AiPlatformConfig config = config("sk-abc", List.of(model))
                .withSpeedResult(new AiSpeedResult("chat", AiSpeedResult.Status.OK,
                        645L, 2000L, null, null, 1L));

        AiPlatformUiState.Row row = mapper.map(settings(config, "chat"), config, null,
                Set.of(), false, false, "").models().rows().get(0);

        assertEquals(List.of("首字 645 ms", "128K 上下文"), row.facts());
        assertEquals(AiPlatformUiState.RowState.OK, row.state());
    }

    @Test
    public void aModelBeingTestedSaysSoInsteadOfShowingOldNumbers() {
        AiModel model = model("chat", null);
        AiPlatformConfig config = config("sk-abc", List.of(model))
                .withSpeedResult(new AiSpeedResult("chat", AiSpeedResult.Status.OK,
                        300L, 900L, null, null, 1L));

        AiPlatformUiState.Row row = mapper.map(settings(config, "chat"), config, null,
                Set.of("chat"), true, false, "").models().rows().get(0);

        assertEquals(AiPlatformUiState.RowState.TESTING, row.state());
    }

    @Test
    public void aFailedProbeIsAFailedRow() {
        AiModel model = model("chat", null);
        AiPlatformConfig config = config("sk-abc", List.of(model))
                .withSpeedResult(new AiSpeedResult("chat", AiSpeedResult.Status.TIMEOUT,
                        -1L, 25_000L, null, null, 1L));

        AiPlatformUiState.Row row = mapper.map(settings(config, "chat"), config, null,
                Set.of(), false, false, "").models().rows().get(0);

        assertEquals(AiPlatformUiState.RowState.FAILED, row.state());
        assertTrue(row.facts().isEmpty());
    }

    @Test
    public void aManualModelWithoutAnythingElseSaysManual() {
        AiModel manual = new AiModel("mine", "mine", null, null, Set.of(), true);
        AiPlatformConfig config = config("sk-abc", List.of(manual));

        AiPlatformUiState.Row row = mapper.map(settings(config, "mine"), config, null,
                Set.of(), false, false, "").models().rows().get(0);

        assertEquals(List.of("手动添加"), row.facts());
    }

    // ---- 区块头与结论 ----

    @Test
    public void theSectionSummaryNamesTheFastestModel() {
        AiModel slow = model("slow", null);
        AiModel fast = model("fast", null);
        AiPlatformConfig config = config("sk-abc", List.of(slow, fast))
                .withSpeedResult(new AiSpeedResult("slow", AiSpeedResult.Status.OK, 1200L, 2000L, null, null, 1L))
                .withSpeedResult(new AiSpeedResult("fast", AiSpeedResult.Status.OK, 420L, 900L, null, null, 1L));

        AiPlatformUiState.Models models = mapper.map(settings(config, "fast"), config, null,
                Set.of(), false, false, "").models();

        assertEquals("最快：fast", models.summary());
        assertEquals("全部测速", models.actionLabel());
    }

    @Test
    public void whileRunningTheSectionActionAsksToStop() {
        AiPlatformConfig config = config("sk-abc", List.of(model("chat", null)));

        AiPlatformUiState.Models models = mapper.map(settings(config, "chat"), config, null,
                Set.of("chat"), true, false, "").models();

        assertEquals("停止", models.actionLabel());
    }

    // ---- 搜索 ----

    @Test
    public void fewModelsDoNotGetASearchBox() {
        AiPlatformConfig config = config("sk-abc", models(AiPlatformUiMapper.SEARCH_THRESHOLD));

        AiPlatformUiState.Models models = mapper.map(settings(config, "m0"), config, null,
                Set.of(), false, false, "abc").models();

        assertFalse("8 个以内一屏扫得完，不该给搜索框", models.searchable());
        assertEquals("没在搜的时候搜索词必须被忽略", "", models.query());
        assertEquals(AiPlatformUiMapper.SEARCH_THRESHOLD, models.rows().size());
    }

    @Test
    public void moreThanThresholdTurnsOnTheSearchBox() {
        AiPlatformConfig config = config("sk-abc", models(AiPlatformUiMapper.SEARCH_THRESHOLD + 1));

        AiPlatformUiState.Models models = mapper.map(settings(config, "m0"), config, null,
                Set.of(), false, false, "").models();

        assertTrue(models.searchable());
        assertEquals(AiPlatformUiMapper.SEARCH_THRESHOLD + 1, models.rows().size());
    }

    @Test
    public void searchFiltersByNameOrIdAndKeepsTheTotal() {
        AiModel chat = model("deepseek-chat", null);
        AiModel reasoner = model("deepseek-reasoner", null);
        AiModel image = new AiModel("sd-xl", "画图模型", null, null, Set.of(), false);
        AiPlatformConfig config = config("sk-abc", List.of(chat, reasoner, image,
                model("a", null), model("b", null), model("c", null), model("d", null),
                model("e", null), model("f", null)));

        AiPlatformUiState.Models byId = mapper.map(settings(config, "a"), config, null,
                Set.of(), false, false, "SD").models();
        AiPlatformUiState.Models byName = mapper.map(settings(config, "a"), config, null,
                Set.of(), false, false, "画图").models();
        AiPlatformUiState.Models none = mapper.map(settings(config, "a"), config, null,
                Set.of(), false, false, "zzz").models();

        assertEquals(1, byId.rows().size());
        assertEquals("sd-xl", byId.rows().get(0).id());
        assertEquals(1, byName.rows().size());
        assertTrue("没有匹配时行是空的，但总数不变", none.rows().isEmpty());
        assertEquals(9, none.count());
        assertEquals("没有匹配时也要保留搜索词，否则搜索框会被清空", "zzz", none.query());
    }

    @Test
    public void theFastestConclusionIgnoresTheFilter() {
        List<AiModel> nine = models(9);
        AiPlatformConfig config = config("sk-abc", nine)
                .withSpeedResult(new AiSpeedResult("m0", AiSpeedResult.Status.OK, 900L, 1200L, null, null, 1L))
                .withSpeedResult(new AiSpeedResult("m8", AiSpeedResult.Status.OK, 300L, 800L, null, null, 1L));

        AiPlatformUiState.Models models = mapper.map(settings(config, "m0"), config, null,
                Set.of(), false, false, "m0").models();

        assertEquals(1, models.rows().size());
        assertEquals("结论比的是平台上全部模型，不是筛选结果", "最快：m8", models.summary());
    }

    @Test
    public void filterIsCaseInsensitiveAndTreatsBlankAsEverything() {
        AiPlatformUiState.Row upper = row("GPT-4o");
        AiPlatformUiState.Row lower = row("deepseek-chat");

        assertEquals(1, AiPlatformUiMapper.filter(List.of(upper, lower), "gpt").size());
        assertEquals(2, AiPlatformUiMapper.filter(List.of(upper, lower), "   ").size());
        assertEquals(2, AiPlatformUiMapper.filter(List.of(upper, lower), "").size());
    }

    // ---- 模式过滤 ----

    @Test
    public void textModeListsChatModelsOnly() {
        AiModel chat = model("deepseek-chat", null);
        AiModel realtime = new AiModel("gpt-4o-realtime-preview", "gpt-4o-realtime-preview", null, null,
                Set.of(com.ggec.earbuds.domain.ai.model.AiModelFeature.REALTIME_AUDIO), false);
        AiPlatformConfig config = config("sk-abc", List.of(chat, realtime));

        AiPlatformUiState.Models models = mapper.map(settings(config, "deepseek-chat"), config, null, Set.of(), false, false, "").models();

        assertEquals(List.of("deepseek-chat"), models.rows().stream().map(AiPlatformUiState.Row::id).toList());
    }

    // ---- 高级 ----

    @Test
    public void advancedSummaryCarriesProtocolAndUrl() {
        AiPlatformConfig config = config("sk-abc", List.of(model("chat", null)));

        AiPlatformUiState.Advanced advanced = mapper.map(settings(config, "chat"), config, null,
                Set.of(), false, false, "").advanced();

        assertTrue(advanced.baseUrl().startsWith("https://"));
        assertEquals("Chat Completions", activityProtocolLabel(advanced));
    }

    /** 协议是资源 id，测试里只为确认它指向对话补全这一条。 */
    private static String activityProtocolLabel(AiPlatformUiState.Advanced advanced) {
        assertEquals(com.ggec.earbuds.R.string.ai_protocol_chat, advanced.protocolRes());
        return "Chat Completions";
    }

    // ---- 夹具 ----

    private static AiModel model(String id, Integer context) {
        return new AiModel(id, id, context, null, Set.of(), false);
    }

    private static List<AiModel> models(int count) {
        List<AiModel> models = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            models.add(model("m" + i, null));
        }
        return models;
    }

    private static AiPlatformUiState.Row row(String id) {
        return new AiPlatformUiState.Row(id, id, false, List.of(),
                AiPlatformUiState.RowState.IDLE, -1L);
    }

    private static AiPlatformConfig config(String key, List<AiModel> models) {
        return new AiPlatformConfig("p1", "deepseek", "DeepSeek", "https://api.deepseek.com",
                key, AiProtocol.CHAT_COMPLETIONS, models, 0L, Map.of());
    }

    private static AiSettings settings(AiPlatformConfig config, String modelId) {
        return new AiSettings(List.of(config), AiInteractionMode.TEXT,
                modelId == null ? null : new AiSelection(config.id(), modelId),
                AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);
    }
}
