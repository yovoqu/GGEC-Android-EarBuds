package com.ggec.earbuds.ui.ai.settings;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AiSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository();
    private final MemoryConversationRepository conversations = new MemoryConversationRepository();
    private final ManualClock clock = new ManualClock();
    private final FakeCatalog catalog = new FakeCatalog();
    private final List<AiSettingsEvent> events = new ArrayList<>();

    private AiSettingsViewModel open() {
        AiChatSession session = new AiChatSession(settings, conversations, new ScriptedChatGateway(), clock, clock,
                Runnable::run);
        AiSettingsViewModel viewModel = new AiSettingsViewModel(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new LoadPlatformTemplates(catalog),
                new SavePlatform(settings, catalog, new AiApiKeyPolicy(), new AiBaseUrlPolicy(catalog), clock),
                new SaveChatPreferences(settings),
                session,
                new AiApiKeyPolicy(),
                new AiBaseUrlPolicy(catalog),
                new AiSettingsUiMapper(),
                Runnable::run);
        viewModel.events().observeForever(events::add);
        return viewModel;
    }

    private AiChatPreferences preferences() {
        return settings.load().preferences();
    }

    @Test
    public void anEmptyInstallListsTheBuiltInPlatformsAsUnconfigured() {
        AiSettingsUiState state = open().uiState().getValue();

        assertTrue(state.loaded());
        assertNull(state.activeModel());
        assertEquals(1, state.platforms().size());
        AiSettingsUiState.PlatformRow row = state.platforms().get(0);
        assertEquals("DeepSeek", row.name());
        assertEquals("deepseek", row.templateId());
        assertNull("nothing exists to open until a key is saved", row.platformId());
        assertFalse(row.configured());
        assertFalse(row.custom());
        assertEquals(0, row.modelCount());
        assertTrue(state.preferences().systemPromptIsDefault());
    }

    @Test
    public void configuredAndCustomPlatformsAreListedWithTheActiveOneFlagged() {
        AiPlatformConfig custom = new AiPlatformConfig("custom-a", "openai-compatible", "公司网关",
                "https://gw.example.com", "sk-1", AiProtocol.CHAT_COMPLETIONS, List.of(model("m1"), model("m2")), 5L,
                Map.of());
        settings.update(s -> AiFixtures.readySettings().withPlatform(custom));

        AiSettingsUiState state = open().uiState().getValue();

        assertEquals(2, state.platforms().size());
        AiSettingsUiState.PlatformRow builtIn = state.platforms().get(0);
        assertEquals("deepseek", builtIn.platformId());
        assertTrue(builtIn.configured());
        assertEquals(2, builtIn.modelCount());
        assertTrue(builtIn.active());
        AiSettingsUiState.PlatformRow added = state.platforms().get(1);
        assertTrue(added.custom());
        assertEquals("公司网关", added.name());
        assertEquals("custom-a", added.platformId());
        assertEquals(2, added.modelCount());
        assertFalse(added.active());
        assertEquals("deepseek", state.activeModel().platformName());
        assertEquals("deepseek-chat", state.activeModel().modelName());
    }

    @Test
    public void theListFollowsChangesMadeElsewhere() {
        AiSettingsViewModel viewModel = open();

        settings.update(s -> s.withPlatform(platform("deepseek", "k", "a")));

        assertTrue(viewModel.uiState().getValue().platforms().get(0).configured());
        assertEquals(1, viewModel.uiState().getValue().platforms().get(0).modelCount());
    }

    @Test
    public void customPlatformsAreValidatedThenCreatedAndOpened() {
        AiSettingsViewModel viewModel = open();

        assertEquals(AiValidationError.NAME_EMPTY, viewModel.addCustomPlatform("openai-compatible", " ", "https://x.example.com", "k"));
        assertEquals(AiValidationError.BASE_URL_REQUIRED, viewModel.addCustomPlatform("openai-compatible", "n", "", "k"));
        assertEquals(AiValidationError.BASE_URL_NOT_HTTPS, viewModel.addCustomPlatform("openai-compatible", "n", "http://x.example.com", "k"));
        assertEquals(AiValidationError.API_KEY_EMPTY, viewModel.addCustomPlatform("openai-compatible", "n", "https://x.example.com", " "));
        assertEquals(0, settings.updates);
        assertTrue(events.isEmpty());

        assertNull(viewModel.addCustomPlatform("openai-compatible", "公司网关", "gw.example.com/v1", " sk-key "));

        AiPlatformConfig created = settings.load().platforms().get(0);
        assertEquals("公司网关", created.name());
        assertEquals("https://gw.example.com", created.baseUrl());
        assertEquals("sk-key", created.apiKey());
        assertEquals(AiSettingsEvent.Type.OPEN_PLATFORM, events.get(0).type());
        assertEquals(created.id(), events.get(0).argument());
        assertEquals(2, viewModel.uiState().getValue().platforms().size());
        assertEquals(1, viewModel.customTemplates().size());
        assertEquals("openai-compatible", viewModel.customTemplates().get(0).id());
    }

    @Test
    public void chatParametersAreSavedOneByOneWithoutOverwritingEachOther() {
        AiSettingsViewModel viewModel = open();

        viewModel.setTemperature(0.7);
        viewModel.setMaxOutputTokens(2048);
        viewModel.setContextMessages(10);
        viewModel.setShowReasoning(false);
        assertNull(viewModel.setSystemPrompt("  你是一个助手  "));

        AiChatPreferences saved = preferences();
        assertEquals(Double.valueOf(0.7), saved.temperature());
        assertEquals(Integer.valueOf(2048), saved.maxOutputTokens());
        assertEquals(10, saved.contextMessages());
        assertFalse(saved.showReasoning());
        assertEquals("你是一个助手", saved.systemPrompt());
        AiSettingsUiState.Preferences shown = viewModel.uiState().getValue().preferences();
        assertEquals(Double.valueOf(0.7), shown.temperature());
        assertFalse(shown.systemPromptIsDefault());
        assertFalse(shown.showReasoning());

        viewModel.setTemperature(null);
        viewModel.setMaxOutputTokens(null);
        assertNull(preferences().temperature());
        assertNull(preferences().maxOutputTokens());
        assertEquals("the other parameters are untouched", 10, preferences().contextMessages());
    }

    @Test
    public void outOfRangeValuesAreBroughtBackIntoRange() {
        AiSettingsViewModel viewModel = open();

        viewModel.setTemperature(5.0);
        assertEquals(Double.valueOf(2.0), preferences().temperature());
        viewModel.setTemperature(-1.0);
        assertEquals(Double.valueOf(0.0), preferences().temperature());
        viewModel.setTemperature(Double.NaN);
        assertNull(preferences().temperature());
        viewModel.setMaxOutputTokens(0);
        assertNull(preferences().maxOutputTokens());
        viewModel.setContextMessages(-5);
        assertEquals(0, preferences().contextMessages());
    }

    @Test
    public void anOverlongSystemPromptIsRejectedAndTheOldOneKept() {
        AiSettingsViewModel viewModel = open();
        viewModel.setSystemPrompt("短提示词");

        AiValidationError error = viewModel.setSystemPrompt("长".repeat(AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH + 1));

        assertEquals(AiValidationError.PROMPT_TOO_LONG, error);
        assertEquals("短提示词", preferences().systemPrompt());
        assertNull(viewModel.setSystemPrompt("长".repeat(AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH)));
    }

    @Test
    public void theDefaultSystemPromptCanBeRestored() {
        AiSettingsViewModel viewModel = open();
        viewModel.setSystemPrompt("改过了");
        assertFalse(viewModel.uiState().getValue().preferences().systemPromptIsDefault());

        viewModel.restoreSystemPrompt();

        assertEquals(AiChatPreferences.DEFAULT_SYSTEM_PROMPT, preferences().systemPrompt());
        assertTrue(viewModel.uiState().getValue().preferences().systemPromptIsDefault());
    }

    @Test
    public void clearingTheHistoryDeletesEveryConversationAndTellsTheUser() {
        conversations.save(new AiConversation("c1", "一", 1, 5, List.of(AiMessage.user("u", "你好", 1))));
        conversations.save(new AiConversation("c2", "二", 1, 6, List.of(AiMessage.user("v", "再见", 1))));
        AiSettingsViewModel viewModel = open();

        viewModel.clearHistory();

        assertTrue(conversations.summaries().isEmpty());
        assertEquals(R.string.ai_pref_cleared, events.get(0).message());
        assertEquals(AiSettingsEvent.Type.TOAST, events.get(0).type());
    }
}
