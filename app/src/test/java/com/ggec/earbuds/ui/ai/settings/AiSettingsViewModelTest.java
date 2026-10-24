package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;
import com.ggec.earbuds.domain.ai.usecase.ApplyModelParams;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.LoadUsageSummary;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceSettings;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;

/**
 * 设置页 ViewModel：把用例执行出来的结果拼成界面状态，并且不把仓库、线程这些东西漏到界面层。
 * 线程用 {@code Runnable::run}，所以这里每一步都是同步的。
 */
public class AiSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final MemoryUsageStore usage = new MemoryUsageStore();
    private final FakeCatalog catalog = new FakeCatalog();
    private AiSettingsViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new AiSettingsViewModel(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new SaveChatPreferences(settings),
                new SaveVoiceSettings(settings),
                new LoadUsageSummary(usage),
                new LoadPlatformTemplates(catalog),
                new ApplyModelParams(settings),
                Runnable::run,
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
        viewModel.selectMode(AiMode.OMNI);

        assertEquals(AiMode.OMNI, settings.load().voice().mode());
        assertTrue(viewModel.uiState().getValue().modes().get(1).selected());
    }

    @Test
    public void parametersRoundTripThroughTheUseCase() {
        viewModel.saveTemperature(0.4);
        viewModel.saveContextMessages(0);

        assertEquals(Double.valueOf(0.4), settings.load().preferences().temperature());
        assertEquals(0, settings.load().preferences().contextMessages());
        AiSettingsUiState.Params params = viewModel.uiState().getValue().params();
        assertEquals(AiText.of(R.string.ai_settings_value_number, "0.4"), params.temperatureText());
    }

    @Test
    public void clearingAParameterGoesBackToPlatformDefault() {
        viewModel.saveTopP(0.5);
        viewModel.saveTopP(null);

        assertNull(settings.load().preferences().topP());
    }

    @Test
    public void usageComesFromTheLedgerAndRefreshesOnDemand() {
        assertFalse(viewModel.uiState().getValue().usage().hasRecords());

        usage.append(new AiUsageRecord(1L, null, "deepseek", "deepseek-chat", 100, 40, 140,
                new BigDecimal("0.0100"), 900L, AiUsageStatus.SUCCESS, null));
        viewModel.reloadUsage();

        AiSettingsUiState.Usage summary = viewModel.uiState().getValue().usage();
        assertTrue(summary.hasRecords());
        assertTrue(summary.costKnown());
        assertEquals(AiText.of(R.string.ai_settings_usage_cost_value, "0.01", "1"), summary.cost());
    }

    @Test
    public void applyingTheCurrentValuesPinsEveryModelOfEveryPlatform() {
        viewModel.saveGlobalParamsEnabled(true);
        viewModel.saveTemperature(0.7);

        viewModel.applyParamsToAllModels();

        AiPlatformConfig platform = settings.load().platform("deepseek").orElseThrow();
        assertEquals(2, platform.modelParams().size());
        assertEquals(Double.valueOf(0.7), platform.modelParams().get("deepseek-chat").temperature());
        assertEquals(Double.valueOf(0.7), platform.modelParams().get("deepseek-reasoner").temperature());
        // 写完每个模型都有自己的一份，"作用范围"随之从"所有模型"变成"当前平台"。
        assertEquals(AiSettingsUiState.Scope.Kind.PLATFORM,
                viewModel.uiState().getValue().params().scope().kind());
    }

    @Test
    public void applyingWithNothingSetWritesNothing() {
        viewModel.applyParamsToAllModels();

        assertTrue("没有值时不能往磁盘上写空覆盖",
                settings.load().platform("deepseek").orElseThrow().modelParams().isEmpty());
    }

    @Test
    public void clearedViewModelsStopListeningToTheRepository() {
        AiSettingsUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        settings.update(current -> current.withSelection(null));

        assertEquals(before, viewModel.uiState().getValue());
    }
}
