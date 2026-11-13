package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;

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

        AiSettingsUiState.PlatformRow row = map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_models, "2"), row.subtitle());
        assertFalse("没有选中模型时不该说它是当前的", row.current());
    }

    @Test
    public void platformWithoutKeySaysSoInsteadOfZeroModels() {
        AiSettings settings = settings(null, platform("deepseek", "", "https://api.deepseek.com", "chat"));

        AiSettingsUiState.PlatformRow row = map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_no_key), row.subtitle());
    }

    @Test
    public void customHostIsPartOfTheSubtitleSoWrongHostIsVisible() {
        AiSettings settings = settings(null, platform("gateway", "sk-live", "https://gw.example.com/v1/", "chat"));

        AiSettingsUiState.PlatformRow row = map(settings).platforms().get(0);

        assertEquals(AiText.of(R.string.ai_settings_platform_summary, "1", "gw.example.com/v1"),
                row.subtitle());
    }

    @Test
    public void thePlatformHoldingTheSelectedModelIsMarked() {
        AiSettings settings = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"),
                platform("other", "sk-other", null, "chat"));

        List<AiSettingsUiState.PlatformRow> rows = map(settings).platforms();

        assertTrue(rows.get(0).current());
        assertFalse(rows.get(1).current());
    }

    // ---- 模式与身份卡 ----

    @Test
    public void textModeShowsTheTextModelAndItsHint() {
        AiSettings settings = settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "sk-live", null, "deepseek-chat", "gpt-4o-realtime-preview"));

        AiSettingsUiState state = map(settings);

        assertEquals(AiInteractionMode.TEXT, state.mode());
        assertEquals(AiText.of(R.string.ai_settings_mode_text_hint), state.modeHint());
        assertFalse(state.identity().empty());
        assertEquals("deepseek", state.identity().platformName());
        assertEquals("deepseek-chat", state.identity().modelName());
    }

    @Test
    public void voiceModeShowsThePresetVoiceModelAndItsKeyState() {
        AiSettings settings = voiceSettings(new AiSelection("openai", "gpt-4o"),
                AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen"),
                platform("openai", "sk-live", null, "gpt-4o"));

        AiSettingsUiState state = mapper.map(settings, AiInteractionMode.VOICE);

        assertEquals(AiInteractionMode.VOICE, state.mode());
        assertEquals(AiText.of(R.string.ai_settings_mode_voice_hint), state.modeHint());
        assertEquals("Qwen", state.voice().modelName());
        assertTrue(state.voice().configured());
        assertEquals(2, state.voice().options().size());
        assertEquals("ChatGPT", state.voice().options().get(0).name());
        assertFalse("没选中的预设不该标成当前", state.voice().options().get(0).current());
        assertTrue(state.voice().options().get(1).current());
        assertEquals("另一家没填 Key", false, state.voice().options().get(0).configured());
    }

    @Test
    public void anEmptyTextModeSaysTheTextModelIsMissing() {
        AiSettings settings = settings(null, platform("deepseek", "sk-live", null, "chat"));

        AiSettingsUiState.Identity identity = map(settings).identity();

        assertTrue(identity.empty());
        assertEquals(R.string.ai_settings_text_empty, identity.emptyTitle());
        assertEquals(R.string.ai_settings_text_empty_hint, identity.emptyHint());
    }

    @Test
    public void anUnconfiguredVoiceModelIsStillSelectedButNotReady() {
        AiSettings settings = voiceSettings(null, AiVoiceConfig.DEFAULT,
                platform("openai", "sk-live", null, "gpt-4o"));

        AiSettingsUiState.Voice voice = mapper.map(settings, AiInteractionMode.VOICE).voice();

        assertEquals("ChatGPT", voice.modelName());
        assertFalse("只选了模型、没填 Key 时不算可用", voice.configured());
        assertTrue(voice.options().get(0).current());
        assertEquals("图标由映射层给出，视图不再按厂商 id 自己选",
                R.drawable.ic_ai_provider_chatgpt, voice.providerIconRes());
        assertEquals(R.drawable.ic_ai_provider_chatgpt, voice.options().get(0).iconRes());
        assertEquals(R.drawable.ic_ai_provider_qwen,
                voice.options().stream()
                        .filter(option -> option.id().equals(AiVoiceModel.QWEN.id()))
                        .findFirst().orElseThrow().iconRes());
    }

    @Test
    public void switchingModeDoesNotMoveTheOtherSelection() {
        AiSettings settings = settings(new AiSelection("deepseek", "chat"),
                platform("deepseek", "sk-live", null, "chat"));

        AiSettingsUiState textState = mapper.map(settings, AiInteractionMode.TEXT);
        AiSettingsUiState voiceState = mapper.map(settings, AiInteractionMode.VOICE);

        assertEquals(AiInteractionMode.TEXT, textState.mode());
        assertEquals(AiInteractionMode.VOICE, voiceState.mode());
        assertTrue("切换展示形态不影响文本选择", settings.textModel().isPresent());
        assertEquals("语音默认预设仍在，但没 Key 不可用", false, voiceState.voice().configured());
        assertEquals(new AiSelection("deepseek", "chat"), settings.textSelection());
    }

    @Test
    public void fetchedVoiceModelsBecomeIdOnlyOptions() {
        AiVoiceConfig voice = AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")
                .withRealtimeModels(List.of(
                        new AiModel("gpt-4o-realtime-preview", "gpt-4o-realtime-preview",
                                null, null, Set.of(), false, AiModelCategory.REALTIME),
                        new AiModel("qwen-audio-3.1-realtime-flash", "qwen-audio-3.1-realtime-flash",
                                null, null, Set.of(), false, AiModelCategory.REALTIME)))
                .withRealtimeModel("gpt-4o-realtime-preview");

        AiSettingsUiState.Voice state = mapper.map(voiceSettings(null, voice),
                AiInteractionMode.VOICE).voice();

        assertEquals(2, state.models().size());
        assertEquals("gpt-4o-realtime-preview", state.models().get(0).id());
        assertEquals("qwen-audio-3.1-realtime-flash", state.models().get(1).id());
        assertEquals("gpt-4o-realtime-preview", state.realtimeModelId());
    }

    @Test
    public void aCustomBaseUrlIsFlaggedSoTheInterfaceCanSayOfficialOrNot() {
        AiVoiceConfig voice = AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")
                .withBaseUrl("https://gw.example.com");

        AiSettingsUiState.Voice state = mapper.map(voiceSettings(null, voice),
                AiInteractionMode.VOICE).voice();

        assertTrue(state.baseUrlCustom());
        assertEquals("https://gw.example.com", state.baseUrl());
        assertEquals(VoiceDuplex.HALF_DUPLEX.id(), state.defaultDuplexId());
    }

    @Test
    public void mapsConfiguredVoiceDefaultDuplex() {
        AiVoiceConfig voice = AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")
                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX);

        AiSettingsUiState.Voice state = mapper.map(voiceSettings(null, voice),
                AiInteractionMode.VOICE).voice();

        assertEquals(VoiceDuplex.FULL_DUPLEX.id(), state.defaultDuplexId());
        assertEquals(2, state.duplexOptions().size());
        assertEquals(VoiceDuplex.FULL_DUPLEX.id(), state.duplexOptions().get(0).id());
        assertEquals(R.string.ai_settings_voice_duplex_full, state.duplexOptions().get(0).labelRes());
        assertTrue(state.duplexOptions().get(0).current());
        assertEquals(VoiceDuplex.HALF_DUPLEX.id(), state.duplexOptions().get(1).id());
        assertEquals(R.string.ai_settings_voice_duplex_half, state.duplexOptions().get(1).labelRes());
        assertFalse(state.duplexOptions().get(1).current());
    }

    // ---- 回答风格 ----
    @Test
    public void theStyleSectionCarriesTheStoredPrompt() {
        AiChatPreferences preferences = AiChatPreferences.DEFAULT.withSystemPrompt("只回答一句话");
        AiSettings settings = new AiSettings(List.of(platform("deepseek", "sk-live", null, "chat")),
                null, AiVoiceConfig.DEFAULT, preferences);

        AiSettingsUiState.Style style = map(settings).style();

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

    private AiSettingsUiState map(AiSettings settings) {
        return mapper.map(settings, AiInteractionMode.TEXT);
    }

    private static AiSettings settings(AiSelection textSelection, AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), textSelection,
                AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);
    }

    private static AiSettings voiceSettings(AiSelection textSelection, AiVoiceConfig voice,
                                            AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), textSelection, voice,
                AiChatPreferences.DEFAULT);
    }

    private static AiPlatformConfig platform(String id, String apiKey, String baseUrl, String... modelIds) {
        List<AiModel> models = new ArrayList<>();
        for (String modelId : modelIds) {
            models.add(new AiModel(modelId, modelId, null, null, Set.of(), false, null));
        }
        return new AiPlatformConfig(id, id, id, baseUrl, apiKey != null && !apiKey.isBlank(),
                AiProtocol.CHAT_COMPLETIONS, models, 0L, Map.of());
    }
}
