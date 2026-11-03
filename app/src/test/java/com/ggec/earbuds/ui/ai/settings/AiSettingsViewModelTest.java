package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.FetchVoiceModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettingsPersistence;
import com.ggec.earbuds.domain.ai.usecase.SaveAiSettings;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.DeferredSettingsRepository;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.MemoryAiUiPreferences;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置页 ViewModel：单一数据源（设置仓库）+ 视图偏好分离。
 */
public class AiSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final FakeCatalog catalog = new FakeCatalog();
    private final List<AiModel> voiceCatalog = new ArrayList<>();
    private final MemoryAiUiPreferences uiPreferences = new MemoryAiUiPreferences();
    private AiSettingsViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = newViewModel(settings, uiPreferences);
        viewModel.start();
    }

    private AiSettingsViewModel newViewModel(MemorySettingsRepository repository,
                                             MemoryAiUiPreferences preferences) {
        return new AiSettingsViewModel(
                new LoadAiSettings(repository),
                new ObserveAiSettings(repository),
                new ObserveAiSettingsPersistence(repository),
                new FetchVoiceModels(repository, (baseUrl, apiKey) -> List.copyOf(voiceCatalog)),
                new SaveAiSettings(repository),
                Runnable::run,
                new LoadPlatformTemplates(catalog),
                preferences,
                new AiSettingsUiMapper());
    }

    @Test
    public void startPublishesTheStoredSettings() {
        AiSettingsUiState state = viewModel.uiState().getValue();

        assertTrue(state.loaded());
        assertEquals(1, state.platforms().size());
        assertEquals("deepseek", state.platforms().get(0).name());
        assertEquals(2, viewModel.templates().getValue().size());
    }

    /**
     * 核心回归：切分段只改视图偏好，绝不写设置文档。
     */
    @Test
    public void switchingTheModeNeverTouchesTheSettingsDocument() {
        AiSettings before = settings.load();

        viewModel.selectMode(AiInteractionMode.VOICE);

        assertEquals("ui state follows the toggle", AiInteractionMode.VOICE,
                viewModel.uiState().getValue().mode());
        assertEquals("view preference is stored", AiInteractionMode.VOICE,
                uiPreferences.lastSettingsMode(AiInteractionMode.TEXT));
        assertEquals("settings document is untouched", before, settings.load());
    }

    @Test
    public void theStoredViewPreferenceDecidesTheOpeningSegment() {
        AiSettingsViewModel fresh = newViewModel(settings, new MemoryAiUiPreferences(AiInteractionMode.VOICE));

        fresh.start();

        assertEquals(AiInteractionMode.VOICE, fresh.uiState().getValue().mode());
    }

    /**
     * 单一数据源：一次操作写入仓库，仓库同步通知，UiState 与仓库立刻一致。
     */
    @Test
    public void aChangeLandsInTheSingleSettingsSourceImmediately() {
        viewModel.saveVoiceApiKey("sk-gpt");

        assertTrue("ui reflects the key", viewModel.uiState().getValue().voice().configured());
        assertTrue("repository is the source", settings.load().voice().configured());
    }

    @Test
    public void consecutiveChangesLandInOrder() {
        viewModel.saveVoiceBaseUrl("https://gw.example.com");
        viewModel.selectChatGptDialect(AiRealtimeDialect.GA.id());
        viewModel.saveVoiceApiKey("sk-gpt");

        AiSettings stored = settings.load();
        assertEquals("https://gw.example.com", stored.voice().baseUrl());
        assertEquals(AiRealtimeDialect.GA, stored.voice().dialect());
        assertTrue(stored.voice().configured());
    }

    @Test
    public void theSystemPromptIsTheOnlyStyleSettingLeft() {
        viewModel.saveSystemPrompt("只回答一句话");

        assertEquals("只回答一句话", settings.load().preferences().systemPrompt());
        AiSettingsUiState.Style style = viewModel.uiState().getValue().style();
        assertEquals("只回答一句话", style.systemPrompt());
        assertEquals(AiText.raw("只回答一句话"), style.promptPreview());
    }

    @Test
    public void switchingTheVoiceModelAndSavingItsKeyGoThroughTheSingleSource() {
        viewModel.selectVoiceModel(AiVoiceModel.QWEN.id());
        viewModel.saveVoiceApiKey("sk-qwen");

        assertEquals(AiVoiceModel.QWEN, settings.load().voice().model());
        assertEquals("sk-qwen", settings.load().voice().apiKey());
        AiSettingsUiState.Voice voice = viewModel.uiState().getValue().voice();
        assertEquals("Qwen", voice.modelName());
        assertTrue(voice.configured());
    }

    @Test
    public void anUnknownVoiceModelIsIgnoredInsteadOfGuessed() {
        viewModel.selectVoiceModel("not-a-preset");

        assertEquals(AiVoiceModel.CHATGPT, settings.load().voice().model());
    }

    @Test
    public void clearingTheViewModelStopsListening() {
        AiSettingsUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        settings.update(current -> current.withTextSelection(null));

        assertEquals(before, viewModel.uiState().getValue());
    }

    @Test
    public void voiceChoicesAreStoredAndShown() {
        voiceCatalog.add(new AiModel("gpt-realtime", "GPT Realtime", null, null,
                Set.of(AiModelFeature.REALTIME_AUDIO), false));
        voiceCatalog.add(new AiModel("gpt-4o-realtime-preview", "Preview", null, null,
                Set.of(AiModelFeature.REALTIME_AUDIO), false));

        viewModel.saveVoiceApiKey("sk-gpt");
        viewModel.saveVoiceBaseUrl("https://gw.example.com/");
        viewModel.selectChatGptDialect("openai_ga");
        viewModel.saveVoiceBaseUrl("ftp://nope");
        viewModel.fetchVoiceModels();
        viewModel.selectChatGptModel("gpt-4o-realtime-preview");

        assertEquals("https://gw.example.com", settings.load().voice().baseUrl());
        assertEquals(AiRealtimeDialect.GA, settings.load().voice().dialect());
        assertEquals("gpt-4o-realtime-preview", settings.load().voice().resolvedRealtimeModel());
        AiSettingsUiState.Voice voice = viewModel.uiState().getValue().voice();
        assertTrue(voice.chatGpt());
        assertEquals("https://gw.example.com", voice.baseUrl());
        assertEquals("gpt-4o-realtime-preview", voice.realtimeModelId());
        assertEquals(2, voice.models().size());
        assertEquals("openai_ga", voice.dialectId());
        assertFalse(voice.fetching());
    }

    @Test
    public void beforeFetchingTheVoiceModelIsNotPretendedToBeSelected() {
        AiSettingsUiState.Voice voice = viewModel.uiState().getValue().voice();

        assertTrue(voice.models().isEmpty());
        assertEquals("没拉取前不能把厂商预设当成已选模型", "", voice.realtimeModelId());
    }

    @Test
    public void anInvalidPromptIsRejectedWithoutTouchingTheSource() {
        viewModel.saveSystemPrompt("ok");
        String oversized = "x".repeat(40_000);

        viewModel.saveSystemPrompt(oversized);

        assertEquals("ok", settings.load().preferences().systemPrompt());
    }

    @Test
    public void startPublishesLoadingBeforeTheRepositoryFinishes() {
        DeferredSettingsRepository deferred = new DeferredSettingsRepository();
        AiSettingsViewModel loading = new AiSettingsViewModel(
                new LoadAiSettings(deferred), new ObserveAiSettings(deferred),
                new ObserveAiSettingsPersistence(deferred),
                new FetchVoiceModels(deferred, (baseUrl, apiKey) -> List.of()),
                new SaveAiSettings(deferred), Runnable::run, new LoadPlatformTemplates(catalog),
                new MemoryAiUiPreferences(), new AiSettingsUiMapper());

        loading.start();

        assertFalse(loading.uiState().getValue().loaded());
        assertEquals(1, deferred.loadRequests);
        deferred.complete(AiFixtures.readySettings());
        assertTrue(loading.uiState().getValue().loaded());
    }
}
