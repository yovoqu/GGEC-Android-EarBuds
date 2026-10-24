package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiUsageSummary;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置总览的取舍：副标题怎么说、什么时候不能点、费用怎么显示。
 * 这三处出错的后果分别是"误导"、"点了没反应"和"把未知当成免费"。
 */
public class AiSettingsUiMapperTest {

    private final AiSettingsUiMapper mapper = new AiSettingsUiMapper();

    // ---- 平台行 ----

    @Test
    public void platformWithKeyShowsModelCount() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat", "reasoner"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings, AiUsageSummary.empty()).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_models, "2"), row.subtitle());
        assertFalse("没有选中模型时不该说它是当前的", row.current());
    }

    @Test
    public void platformWithoutKeySaysSoInsteadOfZeroModels() {
        AiSettings settings = settings(null, platform("deepseek", "", "https://api.deepseek.com", "chat"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings, AiUsageSummary.empty()).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_no_key), row.subtitle());
    }

    @Test
    public void customHostIsPartOfTheSubtitleSoWrongHostIsVisible() {
        AiSettings settings = settings(null, platform("gateway", "sk-live", "https://gw.example.com/v1/", "chat"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings, AiUsageSummary.empty()).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_summary, "1", "gw.example.com/v1"),
                row.subtitle());
    }

    @Test
    public void theRowHoldingTheSelectedModelIsMarked() {
        AiSettings settings = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"),
                platform("other", "sk-other", null, "chat"));

        List<AiSettingsUiState.PlatformRow> rows = mapper.map(settings, AiUsageSummary.empty()).platforms();

        assertTrue(rows.get(0).current());
        assertFalse(rows.get(1).current());
    }

    // ---- 模式行 ----

    @Test
    public void fullDuplexIsDisabledAndExplainsWhyWithoutARealtimeModel() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat"));

        List<AiSettingsUiState.ModeRow> modes = mapper.map(settings, AiUsageSummary.empty()).modes();

        AiSettingsUiState.ModeRow omni = modes.get(1);
        assertEquals(AiMode.OMNI, omni.mode());
        assertFalse("没有实时模型时全双工不能点", omni.enabled());
        assertEquals(AiText.of(R.string.ai_settings_mode_omni_needs_model), omni.subtitle());
        assertTrue("级联模式任何时候都能点", modes.get(0).enabled());
    }

    @Test
    public void fullDuplexBecomesAvailableWithARealtimeModel() {
        AiVoiceSettings voice = AiVoiceSettings.defaults().withRealtimeModel("gpt-4o-realtime-preview");
        AiSettings settings = new AiSettings(
                List.of(platform("openai", "sk-live", null, "gpt-4o-realtime-preview")),
                null, AiChatPreferences.DEFAULT, voice);

        AiSettingsUiState.ModeRow omni = mapper.map(settings, AiUsageSummary.empty()).modes().get(1);

        assertTrue(omni.enabled());
        assertEquals(AiText.of(R.string.ai_settings_mode_omni_hint), omni.subtitle());
        assertFalse(omni.selected());
    }

    @Test
    public void theStoredModeIsTheSelectedRow() {
        AiSettings settings = new AiSettings(List.of(platform("openai", "sk-live", null, "chat")), null,
                AiChatPreferences.DEFAULT,
                AiVoiceSettings.defaults().withMode(AiMode.OMNI).withRealtimeModel("gpt-4o-realtime-preview"));

        List<AiSettingsUiState.ModeRow> modes = mapper.map(settings, AiUsageSummary.empty()).modes();

        assertFalse(modes.get(0).selected());
        assertTrue(modes.get(1).selected());
    }

    // ---- 参数 ----

    @Test
    public void emptySamplingParametersSayPlatformDefault() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat"));

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertEquals(AiText.of(R.string.ai_settings_value_platform_default), params.temperatureText());
        assertNull(params.temperature());
        assertEquals(AiText.of(R.string.ai_settings_value_platform_default), params.maxOutputText());
    }

    @Test
    public void numbersAndContextAreFormattedWithTheirUnits() {
        AiChatPreferences preferences = AiChatPreferences.DEFAULT
                .withTemperature(0.7)
                .withTopP(0.95)
                .withMaxOutputTokens(1024)
                .withContextMessages(20);
        AiSettings settings = new AiSettings(List.of(platform("deepseek", "sk-live", null, "chat")), null,
                preferences, AiVoiceSettings.defaults());

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertEquals(AiText.of(R.string.ai_settings_value_number, "0.7"), params.temperatureText());
        assertEquals(AiText.of(R.string.ai_settings_value_number, "0.95"), params.topPText());
        assertEquals(AiText.of(R.string.ai_settings_value_number, "1024"), params.maxOutputText());
        assertEquals(AiText.of(R.string.ai_settings_value_context, "20"), params.contextText());
    }

    @Test
    public void zeroContextMeansEverythingAndIsSaidThatWay() {
        AiChatPreferences preferences = AiChatPreferences.DEFAULT.withContextMessages(0);
        AiSettings settings = new AiSettings(List.of(platform("deepseek", "sk-live", null, "chat")), null,
                preferences, AiVoiceSettings.defaults());

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertEquals(AiText.of(R.string.ai_settings_value_context_all), params.contextText());
    }

    @Test
    public void theDefaultPromptIsNamedInsteadOfPreviewed() {
        AiText preview = AiSettingsUiMapper.promptPreview(AiChatPreferences.DEFAULT_SYSTEM_PROMPT);

        assertEquals(AiText.of(R.string.ai_settings_prompt_default), preview);
    }

    @Test
    public void aCustomPromptIsFlattenedAndTruncated() {
        String prompt = "你是助手。\n\n  请只回答一句话，"
                + "不要展开，也不要列举多个可能性，保持简短并且直接给出结论。";

        AiText preview = AiSettingsUiMapper.promptPreview(prompt);

        assertTrue(preview.isRaw());
        assertTrue("换行要压平", !preview.raw().contains("\n"));
        assertTrue(preview.raw().endsWith("…"));
        assertEquals(AiSettingsUiMapper.PROMPT_PREVIEW_LENGTH + 1, preview.raw().length());
    }

    // ---- 用量 ----

    @Test
    public void usageWithoutRecordsHasNoNumbersAtAll() {
        AiSettingsUiState.Usage usage = mapper.map(settings(null, platform("p", "k", null, "m")),
                AiUsageSummary.empty()).usage();

        assertFalse(usage.hasRecords());
        assertNull(usage.requests());
        assertNull(usage.tokens());
        assertNull(usage.cost());
        assertFalse(usage.costKnown());
    }

    @Test
    public void usageWithoutAnyReportedCostSaysUnpriced() {
        AiUsageSummary summary = new AiUsageSummary(3, 1200L, 800L, 2000L, BigDecimal.ZERO, 0);

        AiSettingsUiState.Usage usage = mapper.map(settings(null, platform("p", "k", null, "m")), summary).usage();

        assertTrue(usage.hasRecords());
        assertFalse("一条都没报钱不等于 0 元", usage.costKnown());
        assertEquals(AiText.of(R.string.ai_settings_usage_cost_unknown), usage.cost());
        assertEquals(AiText.of(R.string.ai_settings_usage_requests_value, "3"), usage.requests());
        assertEquals(AiText.of(R.string.ai_settings_usage_tokens_value, "1,200", "800"), usage.tokens());
    }

    @Test
    public void reportedCostCarriesTheAmountAndHowManyRequestsReportedIt() {
        AiUsageSummary summary = new AiUsageSummary(8, 0L, 0L, 0L, new BigDecimal("0.4157"), 2);

        AiSettingsUiState.Usage usage = mapper.map(settings(null, platform("p", "k", null, "m")), summary).usage();

        assertTrue(usage.costKnown());
        assertEquals(AiText.of(R.string.ai_settings_usage_cost_value, "0.42", "2"), usage.cost());
    }

    // ---- 作用范围 ----

    @Test
    public void aDisabledGlobalSwitchMeansTheValuesReachNobody() {
        AiSettings settings = withPreferences(platform("p", "k", null, "chat"),
                preferences -> preferences.withTemperature(0.7));

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertEquals(AiSettingsUiState.Scope.Kind.DISABLED, params.scope().kind());
        assertFalse("没启用就没什么可批量应用的", params.canApply());
    }

    @Test
    public void withoutModelOverridesTheScopeIsEveryModel() {
        AiSettings settings = globalOn(platform("p", "k", null, "chat", "reasoner"));

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertEquals(AiSettingsUiState.Scope.Kind.ALL, params.scope().kind());
        assertEquals(0, params.scope().pinned());
        assertTrue(params.canApply());
        assertEquals(2, params.totalModels());
    }

    @Test
    public void aSinglePinNamesItsModel() {
        AiPlatformConfig platform = platform("p", "k", null, "chat", "reasoner")
                .withModelParams("chat", new AiModelParams(0.3, null, null));
        AiSettings settings = globalOn(platform, "p", "chat");

        AiSettingsUiState.Scope scope = mapper.map(settings, AiUsageSummary.empty()).params().scope();

        assertEquals(AiSettingsUiState.Scope.Kind.ONE_MODEL, scope.kind());
        assertEquals("chat", scope.subject());
        assertEquals(1, scope.pinned());
    }

    @Test
    public void everyModelOfTheActivePlatformPinnedIsThePlatformScope() {
        AiPlatformConfig platform = platform("p", "k", null, "chat", "reasoner")
                .withModelParams("chat", new AiModelParams(0.3, null, null))
                .withModelParams("reasoner", new AiModelParams(null, 0.9, null));
        AiSettings settings = globalOn(platform, "p", "chat");

        AiSettingsUiState.Scope scope = mapper.map(settings, AiUsageSummary.empty()).params().scope();

        assertEquals(AiSettingsUiState.Scope.Kind.PLATFORM, scope.kind());
        assertEquals("副行要能说清是哪个平台", "p", scope.subject());
        assertEquals(2, scope.pinned());
    }

    @Test
    public void partialScopeNamesTheModelsItCanFit() {
        AiPlatformConfig active = platform("p", "k", null, "chat", "reasoner", "chat-mini");
        AiPlatformConfig other = platform("q", "k", null, "x")
                .withModelParams("x", new AiModelParams(1.2, null, null));
        AiPlatformConfig pinned = active
                .withModelParams("chat", new AiModelParams(0.3, null, null))
                .withModelParams("chat-mini", new AiModelParams(0.4, null, null));
        AiSettings settings = new AiSettings(List.of(pinned, other),
                new AiSelection("p", "chat"),
                AiChatPreferences.DEFAULT.withGlobalParamsEnabled(true).withTemperature(0.7),
                AiVoiceSettings.defaults());

        AiSettingsUiState.Scope scope = mapper.map(settings, AiUsageSummary.empty()).params().scope();

        assertEquals(AiSettingsUiState.Scope.Kind.PARTIAL, scope.kind());
        // 三个模型有覆盖，副行只点名前两个：多了会挤成一片。
        assertEquals("chat、chat-mini", scope.subject());
        assertEquals(3, scope.pinned());
    }

    @Test
    public void pinsElsewhereAreJustPartial() {
        AiPlatformConfig active = platform("p", "k", null, "chat", "reasoner");
        AiPlatformConfig other = platform("q", "k", null, "x")
                .withModelParams("x", new AiModelParams(1.2, null, null));
        AiSettings settings = new AiSettings(List.of(active, other),
                new AiSelection("p", "chat"),
                AiChatPreferences.DEFAULT.withGlobalParamsEnabled(true).withTemperature(0.7),
                AiVoiceSettings.defaults());

        AiSettingsUiState.Scope scope = mapper.map(settings, AiUsageSummary.empty()).params().scope();

        assertEquals(AiSettingsUiState.Scope.Kind.PARTIAL, scope.kind());
        assertEquals(1, scope.pinned());
        assertEquals("总数要把别的平台的模型也数进来", 3,
                mapper.map(settings, AiUsageSummary.empty()).params().totalModels());
    }

    @Test
    public void withoutAnyModelThereIsNothingToApplyTo() {
        AiSettings settings = new AiSettings(List.of(), null,
                AiChatPreferences.DEFAULT.withGlobalParamsEnabled(true).withTemperature(0.7),
                AiVoiceSettings.defaults());

        AiSettingsUiState.Params params = mapper.map(settings, AiUsageSummary.empty()).params();

        assertFalse(params.canApply());
        assertEquals(0, params.totalModels());
    }

    // ---- 夹具 ----

    private static AiSettings globalOn(AiPlatformConfig platform) {
        return globalOn(platform, null, null);
    }

    private static AiSettings globalOn(AiPlatformConfig platform, String platformId, String modelId) {
        return new AiSettings(List.of(platform),
                platformId == null ? null : new AiSelection(platformId, modelId),
                AiChatPreferences.DEFAULT.withGlobalParamsEnabled(true).withTemperature(0.7),
                AiVoiceSettings.defaults());
    }

    private static AiSettings withPreferences(AiPlatformConfig platform,
                                              java.util.function.UnaryOperator<AiChatPreferences> change) {
        return new AiSettings(List.of(platform), null,
                change.apply(AiChatPreferences.DEFAULT), AiVoiceSettings.defaults());
    }

    private static AiSettings settings(AiSelection selection, AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), selection, AiChatPreferences.DEFAULT,
                AiVoiceSettings.defaults());
    }

    private static AiPlatformConfig platform(String id, String apiKey, String baseUrl, String... modelIds) {
        List<AiModel> models = new ArrayList<>();
        for (String modelId : modelIds) {
            models.add(new AiModel(modelId, modelId, null, null, Set.of(), false));
        }
        return new AiPlatformConfig(id, id, id, baseUrl, apiKey, AiProtocol.CHAT_COMPLETIONS, models, 0L, Map.of());
    }
}
