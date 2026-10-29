package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceApiKey;
import com.ggec.earbuds.domain.ai.usecase.SelectInteractionMode;
import com.ggec.earbuds.domain.ai.usecase.SelectVoiceModel;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * 设置页 ViewModel：把用例执行出来的结果拼成界面状态，并且不把仓库、线程这些东西漏到界面层。
 */
public class AiSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final FakeCatalog catalog = new FakeCatalog();
    private AiSettingsViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new AiSettingsViewModel(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new SaveChatPreferences(settings),
                new SelectInteractionMode(settings),
                new SelectVoiceModel(settings),
                new SaveVoiceApiKey(settings),
                new LoadPlatformTemplates(catalog),
                new AiSettingsUiMapper());
        viewModel.start();
    }

    @Test
    public void startPublishesTheStoredSettings() {
        AiSettingsUiState state = viewModel.uiState().getValue();

        assertTrue(state.loaded());
        assertEquals(1, state.platforms().size());
        assertEquals("deepseek", state.platforms().get(0).name());
        assertEquals(2, viewModel.templates().size());
    }

    @Test
    public void switchingTheModeIsStoredImmediately() {
        viewModel.selectMode(AiInteractionMode.VOICE);

        assertEquals(AiInteractionMode.VOICE, settings.load().mode());
        assertEquals(AiInteractionMode.VOICE, viewModel.uiState().getValue().mode());
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
        viewModel.selectMode(AiInteractionMode.VOICE);
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
    public void clearedViewModelsStopListeningToTheRepository() {
        AiSettingsUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        settings.update(current -> current.withTextSelection(null));

        assertEquals(before, viewModel.uiState().getValue());
    }
}
