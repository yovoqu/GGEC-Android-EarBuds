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
import com.ggec.earbuds.testing.ai.ImmediateAiDebouncer;
import com.ggec.earbuds.testing.ai.ManualAiDebouncer;
import com.ggec.earbuds.testing.ai.MemoryAiUiPreferences;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置页 ViewModel：本地优先 + 节流落盘 + 视图偏好与配置分离。
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
        viewModel = new AiSettingsViewModel(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new ObserveAiSettingsPersistence(settings),
                new FetchVoiceModels(settings, (baseUrl, apiKey) -> List.copyOf(voiceCatalog)),
                new SaveAiSettings(settings),
                Runnable::run,
                new LoadPlatformTemplates(catalog),
                uiPreferences,
                new ImmediateAiDebouncer(),
                new AiSettingsUiMapper());
        viewModel.start();
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
     * 核心回归：切分段只改视图偏好，绝不写设置文档——这就是之前"每次切换闪一下保存、页面卡顿"的根因。
     */
    @Test
    public void switchingTheModeNeverTouchesTheSettingsDocument() {
        assertEquals(AiInteractionMode.TEXT, settings.load().mode());

        viewModel.selectMode(AiInteractionMode.VOICE);

        assertEquals("ui state follows the toggle", AiInteractionMode.VOICE,
                viewModel.uiState().getValue().mode());
        assertEquals("view preference is stored", AiInteractionMode.VOICE,
                uiPreferences.lastSettingsMode(AiInteractionMode.TEXT));
        assertEquals("settings document is untouched", AiInteractionMode.TEXT, settings.load().mode());
    }

    @Test
    public void theStoredViewPreferenceDecidesTheOpeningSegment() {
        MemoryAiUiPreferences reopened = new MemoryAiUiPreferences(AiInteractionMode.VOICE);
        AiSettingsViewModel fresh = new AiSettingsViewModel(
                new LoadAiSettings(settings), new ObserveAiSettings(settings),
                new ObserveAiSettingsPersistence(settings),
                new FetchVoiceModels(settings, (baseUrl, apiKey) -> List.of()),
                new SaveAiSettings(settings), Runnable::run, new LoadPlatformTemplates(catalog),
                reopened, new ImmediateAiDebouncer(), new AiSettingsUiMapper());

        fresh.start();

        assertEquals(AiInteractionMode.VOICE, fresh.uiState().getValue().mode());
    }

    /**
     * 本地优先：界面状态立刻变，仓库写入延后到节流窗口——不需要等一次磁盘往返才重绘。
     */
    @Test
    public void aChangeUpdatesUiBeforeItReachesTheRepository() {
        ManualAiDebouncer debouncer = new ManualAiDebouncer();
        AiSettingsViewModel gated = new AiSettingsViewModel(
                new LoadAiSettings(settings), new ObserveAiSettings(settings),
                new ObserveAiSettingsPersistence(settings),
                new FetchVoiceModels(settings, (baseUrl, apiKey) -> List.of()),
                new SaveAiSettings(settings), Runnable::run, new LoadPlatformTemplates(catalog),
                new MemoryAiUiPreferences(), debouncer, new AiSettingsUiMapper());
        gated.start();

        gated.saveVoiceApiKey("sk-gpt");

        assertTrue("ui reflects the key immediately", gated.uiState().getValue().voice().configured());
        assertEquals("repository waits for the debounce window", AiVoiceModel.CHATGPT,
                settings.load().voice().model());
        assertFalse("key not persisted yet", settings.load().voice().configured());

        debouncer.runPending();

        assertTrue(settings.load().voice().configured());
    }

    @Test
    public void consecutiveChangesCollapseIntoOneWrite() {
        ManualAiDebouncer debouncer = new ManualAiDebouncer();
        AiSettingsViewModel gated = new AiSettingsViewModel(
                new LoadAiSettings(settings), new ObserveAiSettings(settings),
                new ObserveAiSettingsPersistence(settings),
                new FetchVoiceModels(settings, (baseUrl, apiKey) -> List.of()),
                new SaveAiSettings(settings), Runnable::run, new LoadPlatformTemplates(catalog),
                new MemoryAiUiPreferences(), debouncer, new AiSettingsUiMapper());
        gated.start();

        gated.saveVoiceBaseUrl("https://gw.example.com");
        gated.selectChatGptDialect(AiRealtimeDialect.GA.id());
        gated.saveVoiceApiKey("sk-gpt");
        debouncer.runPending();

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
    public void switchingTheVoiceModelAndSavingItsKeyGoThroughUseCases() {
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
    public void anInvalidPromptIsRejectedWithoutTouchingTheDraft() {
        viewModel.saveSystemPrompt("ok");
        String oversized = "x".repeat(40_000);

        viewModel.saveSystemPrompt(oversized);

        assertEquals("ok", viewModel.uiState().getValue().style().systemPrompt());
    }

    @Test
    public void startPublishesLoadingBeforeTheRepositoryFinishes() {
        DeferredSettingsRepository deferred = new DeferredSettingsRepository();
        AiSettingsViewModel loading = new AiSettingsViewModel(
                new LoadAiSettings(deferred), new ObserveAiSettings(deferred),
                new ObserveAiSettingsPersistence(deferred),
                new FetchVoiceModels(deferred, (baseUrl, apiKey) -> List.of()),
                new SaveAiSettings(deferred), Runnable::run, new LoadPlatformTemplates(catalog),
                new MemoryAiUiPreferences(), new ImmediateAiDebouncer(), new AiSettingsUiMapper());

        loading.start();

        assertFalse(loading.uiState().getValue().loaded());
        assertEquals(1, deferred.loadRequests);
        deferred.complete(AiFixtures.readySettings());
        assertTrue(loading.uiState().getValue().loaded());
    }
}
