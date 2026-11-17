package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.FetchVoiceModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettingsPersistence;
import com.ggec.earbuds.domain.ai.usecase.SaveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceApiKey;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.data.ai.store.EncryptedCredentialStore;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeCredentialStore;
import com.ggec.earbuds.testing.ai.DeferredSettingsRepository;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.MemorySecretStore;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置页 ViewModel：单一数据源（设置仓库），视图偏好也在这份文档里。
 */
public class AiSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySecretStore secrets = new MemorySecretStore();
    private final MemorySettingsRepository settings =
            new MemorySettingsRepository(AiFixtures.readySettings(), secrets);
    private final FakeCatalog catalog = new FakeCatalog();
    private final List<AiModel> voiceCatalog = new ArrayList<>();
    /** 与仓库共用同一份密钥库：仓库写、网关读，和生产同形。 */
    private final EncryptedCredentialStore credentials = new EncryptedCredentialStore(secrets);
    private AiSettingsViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = newViewModel(settings);
        viewModel.start();
    }

    private AiSettingsViewModel newViewModel(MemorySettingsRepository repository) {
        return new AiSettingsViewModel(
                new LoadAiSettings(repository),
                new ObserveAiSettings(repository),
                new ObserveAiSettingsPersistence(repository),
                new FetchVoiceModels(repository, (baseUrl, apiKey) -> List.copyOf(voiceCatalog), credentials),
                new SaveAiSettings(repository),
                Runnable::run,
                new LoadPlatformTemplates(catalog),
                new SaveVoiceApiKey(repository),
                new AiSettingsUiMapper());
    }

    @Test
    public void startPublishesTheStoredSettings() {
        AiSettingsUiState state = viewModel.uiState().getValue();

        assertTrue(state.loaded());
        assertEquals(1, state.platforms().size());
        assertEquals("deepseek", state.platforms().get(0).name());
        assertEquals(2, viewModel.uiState().getValue().templates().size());
    }

    /**
     * 核心回归：切分段写的是设置文档里的视图偏好，没有第二份偏好存储。
     */
    @Test
    public void switchingTheModeLandsInTheSameSettingsDocument() {
        viewModel.selectMode(AiInteractionMode.VOICE);

        assertEquals("ui state follows the toggle", AiInteractionMode.VOICE,
                viewModel.uiState().getValue().mode());
        assertEquals("view preference lives in the settings document", AiInteractionMode.VOICE,
                settings.load().preferences().lastSettingsMode());
    }

    @Test
    public void theStoredViewPreferenceDecidesTheOpeningSegment() {
        settings.update(s -> s.withPreferences(
                s.preferences().withLastSettingsMode(AiInteractionMode.VOICE)));
        AiSettingsViewModel fresh = newViewModel(settings);

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
        viewModel.saveVoiceApiKey("sk-gpt");

        AiSettings stored = settings.load();
        assertEquals("https://gw.example.com", stored.voice().baseUrl());
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
        assertTrue(settings.load().voice().configured());
        AiSettingsUiState.Voice voice = viewModel.uiState().getValue().voice();
        assertEquals("Qwen", voice.modelName());
        assertTrue(voice.configured());
    }

    @Test
    public void selectingDefaultDuplexPersistsThroughSingleSourceWithoutDroppingKeys() {
        viewModel.saveVoiceApiKey("sk-gpt");
        assertEquals(VoiceDuplex.HALF_DUPLEX.id(), viewModel.uiState().getValue().voice().defaultDuplexId());

        viewModel.selectDefaultDuplex(VoiceDuplex.FULL_DUPLEX.id());

        assertEquals(VoiceDuplex.FULL_DUPLEX.id(), viewModel.uiState().getValue().voice().defaultDuplexId());
        assertEquals(VoiceDuplex.FULL_DUPLEX, settings.load().voice().defaultDuplex());
        assertTrue(settings.load().voice().configured());

        viewModel.selectDefaultDuplex(VoiceDuplex.HALF_DUPLEX.id());

        assertEquals(VoiceDuplex.HALF_DUPLEX.id(), viewModel.uiState().getValue().voice().defaultDuplexId());
        assertEquals(VoiceDuplex.HALF_DUPLEX, settings.load().voice().defaultDuplex());
        assertTrue(settings.load().voice().configured());
    }

    @Test
    public void anUnknownDefaultDuplexIsIgnoredInsteadOfGuessed() {
        viewModel.selectDefaultDuplex("alien_mode");

        assertEquals(VoiceDuplex.HALF_DUPLEX, settings.load().voice().defaultDuplex());
        assertEquals(Integer.valueOf(R.string.ai_settings_value_invalid),
                Integer.valueOf(viewModel.events().getValue().message()));
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
                Set.of(), false, AiModelCategory.REALTIME));
        voiceCatalog.add(new AiModel("gpt-4o-realtime-preview", "Preview", null, null,
                Set.of(), false, AiModelCategory.REALTIME));

        viewModel.saveVoiceApiKey("sk-gpt");
        viewModel.saveVoiceBaseUrl("https://gw.example.com/");
        viewModel.saveVoiceBaseUrl("ftp://nope");
        viewModel.fetchVoiceModels();
        viewModel.selectRealtimeModel("gpt-4o-realtime-preview");

        assertEquals("https://gw.example.com", settings.load().voice().baseUrl());
        assertEquals("gpt-4o-realtime-preview", settings.load().voice().resolvedRealtimeModel());
        AiSettingsUiState.Voice voice = viewModel.uiState().getValue().voice();
        assertEquals("https://gw.example.com", voice.baseUrl());
        assertEquals("gpt-4o-realtime-preview", voice.realtimeModelId());
        assertEquals(2, voice.models().size());
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
                new FetchVoiceModels(deferred, (baseUrl, apiKey) -> List.of(), new FakeCredentialStore()),
                new SaveAiSettings(deferred), Runnable::run, new LoadPlatformTemplates(catalog),
                new SaveVoiceApiKey(deferred),
                new AiSettingsUiMapper());

        loading.start();

        assertFalse(loading.uiState().getValue().loaded());
        assertEquals(1, deferred.loadRequests);
        deferred.complete(AiFixtures.readySettings());
        assertTrue(loading.uiState().getValue().loaded());
    }
}
