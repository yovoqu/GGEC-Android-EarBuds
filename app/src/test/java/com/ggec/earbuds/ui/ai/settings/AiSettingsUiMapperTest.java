package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置总览的取舍：身份卡按模式取哪个模型、空态说缺哪一类、提示词怎么预览。
 * 这三处出错的后果分别是"显示错模型"、"用户不知道去配什么"和"看不出改没改"。
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
    public void thePlatformHoldingTheSelectedModelIsMarked() {
        AiSettings settings = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"),
                platform("other", "sk-other", null, "chat"));

        List<AiSettingsUiState.PlatformRow> rows = mapper.map(settings).platforms();

        assertTrue(rows.get(0).current());
        assertFalse(rows.get(1).current());
    }

    // ---- 模式与身份卡 ----

    @Test
    public void textModeShowsTheTextModelAndItsHint() {
        AiSettings settings = settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "sk-live", null, "deepseek-chat", "gpt-4o-realtime-preview"));

        AiSettingsUiState state = mapper.map(settings);

        assertEquals(AiInteractionMode.TEXT, state.mode());
        assertEquals(AiText.of(R.string.ai_settings_mode_text_hint), state.modeHint());
        assertFalse(state.identity().empty());
        assertEquals("deepseek", state.identity().platformName());
        assertEquals("deepseek-chat", state.identity().modelName());
    }

    @Test
    public void voiceModeShowsTheRealtimeModelEvenWhenTextHasAnotherOne() {
        AiSettings settings = voiceSettings(new AiSelection("openai", "gpt-4o"),
                new AiSelection("openai", "gpt-4o-realtime-preview"),
                platform("openai", "sk-live", null, "gpt-4o", "gpt-4o-realtime-preview"));

        AiSettingsUiState state = mapper.map(settings);

        assertEquals(AiInteractionMode.VOICE, state.mode());
        assertEquals(AiText.of(R.string.ai_settings_mode_voice_hint), state.modeHint());
        assertEquals("gpt-4o-realtime-preview", state.identity().modelName());
        assertTrue("平台行按语音选择标当前", state.platforms().get(0).current());
    }

    @Test
    public void anEmptyTextModeSaysTheTextModelIsMissing() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat"));

        AiSettingsUiState.Identity identity = mapper.map(settings).identity();

        assertTrue(identity.empty());
        assertEquals(R.string.ai_settings_text_empty, identity.emptyTitle());
        assertEquals(R.string.ai_settings_text_empty_hint, identity.emptyHint());
    }

    @Test
    public void anEmptyVoiceModeSaysTheVoiceModelIsMissing() {
        AiSettings settings = voiceSettings(new AiSelection("openai", "gpt-4o"), null,
                platform("openai", "sk-live", null, "gpt-4o"));

        AiSettingsUiState.Identity identity = mapper.map(settings).identity();

        assertTrue(identity.empty());
        assertEquals(R.string.ai_settings_voice_empty, identity.emptyTitle());
        assertEquals(R.string.ai_settings_voice_empty_hint, identity.emptyHint());
        assertNull(identity.modelName());
    }

    @Test
    public void switchingModeDoesNotMoveTheOtherSelection() {
        AiSettings text = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"));

        AiSettings voice = text.withMode(AiInteractionMode.VOICE);

        assertEquals(AiInteractionMode.VOICE, voice.mode());
        assertTrue(voice.textModel().isPresent());
        assertTrue("语音还没选模型，身份卡应为空", mapper.map(voice).identity().empty());
        assertEquals(new AiSelection("deepseek", "chat"), voice.textSelection());
    }

    // ---- 回答风格 ----

    @Test
    public void theStyleSectionCarriesTheStoredPrompt() {
        AiChatPreferences preferences = AiChatPreferences.DEFAULT.withSystemPrompt("只回答一句话");
        AiSettings settings = new AiSettings(List.of(platform("deepseek", "sk-live", null, "chat")),
                AiInteractionMode.TEXT, null, null, preferences);

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

    private static AiSettings settings(AiSelection textSelection, AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), AiInteractionMode.TEXT, textSelection, null,
                AiChatPreferences.DEFAULT);
    }

    private static AiSettings voiceSettings(AiSelection textSelection, AiSelection voiceSelection,
                                            AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), AiInteractionMode.VOICE, textSelection, voiceSelection,
                AiChatPreferences.DEFAULT);
    }

    private static AiPlatformConfig platform(String id, String apiKey, String baseUrl, String... modelIds) {
        List<AiModel> models = new ArrayList<>();
        for (String modelId : modelIds) {
            Set<AiModelFeature> features = modelId.contains("realtime")
                    ? Set.of(AiModelFeature.REALTIME_AUDIO) : Set.of();
            models.add(new AiModel(modelId, modelId, null, null, features, false));
        }
        return new AiPlatformConfig(id, id, id, baseUrl, apiKey, AiProtocol.CHAT_COMPLETIONS, models, 0L, Map.of());
    }
}
