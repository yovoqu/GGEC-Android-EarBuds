package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置总览的取舍：副标题怎么说、什么时候不能点、提示词怎么预览。
 * 这三处出错的后果分别是"误导"、"点了没反应"和"看不出改没改"。
 */
public class AiSettingsUiMapperTest {

    private final AiSettingsUiMapper mapper = new AiSettingsUiMapper();

    // ---- 平台行 ----

    @Test
    public void platformWithKeyShowsModelCount() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat", "reasoner"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_models, "2"), row.subtitle());
        assertFalse("没有选中模型时不该说它是当前的", row.current());
    }

    @Test
    public void platformWithoutKeySaysSoInsteadOfZeroModels() {
        AiSettings settings = settings(null, platform("deepseek", "", "https://api.deepseek.com", "chat"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_no_key), row.subtitle());
    }

    @Test
    public void customHostIsPartOfTheSubtitleSoWrongHostIsVisible() {
        AiSettings settings = settings(null, platform("gateway", "sk-live", "https://gw.example.com/v1/", "chat"));

        AiSettingsUiState.PlatformRow row = mapper.map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_summary, "1", "gw.example.com/v1"),
                row.subtitle());
    }

    @Test
    public void theRowHoldingTheSelectedModelIsMarked() {
        AiSettings settings = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"),
                platform("other", "sk-other", null, "chat"));

        List<AiSettingsUiState.PlatformRow> rows = mapper.map(settings).platforms();

        assertTrue(rows.get(0).current());
        assertFalse(rows.get(1).current());
    }

    // ---- 模式行 ----

    @Test
    public void fullDuplexIsDisabledAndExplainsWhyWithoutARealtimeModel() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat"));

        List<AiSettingsUiState.ModeRow> modes = mapper.map(settings).modes();

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

        AiSettingsUiState.ModeRow omni = mapper.map(settings).modes().get(1);

        assertTrue(omni.enabled());
        assertEquals(AiText.of(R.string.ai_settings_mode_omni_hint), omni.subtitle());
        assertFalse(omni.selected());
    }

    @Test
    public void theStoredModeIsTheSelectedRow() {
        AiSettings settings = new AiSettings(List.of(platform("openai", "sk-live", null, "chat")), null,
                AiChatPreferences.DEFAULT,
                AiVoiceSettings.defaults().withMode(AiMode.OMNI).withRealtimeModel("gpt-4o-realtime-preview"));

        List<AiSettingsUiState.ModeRow> modes = mapper.map(settings).modes();

        assertFalse(modes.get(0).selected());
        assertTrue(modes.get(1).selected());
    }

    // ---- 回答风格 ----

    @Test
    public void theStyleSectionCarriesTheStoredPrompt() {
        AiChatPreferences preferences = AiChatPreferences.DEFAULT.withSystemPrompt("只回答一句话");
        AiSettings settings = new AiSettings(List.of(platform("deepseek", "sk-live", null, "chat")), null,
                preferences, AiVoiceSettings.defaults());

        AiSettingsUiState.Style style = mapper.map(settings).style();

        assertEquals("只回答一句话", style.systemPrompt());
        assertEquals(AiText.raw("只回答一句话"), style.promptPreview());
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

    // ---- 夹具 ----

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
