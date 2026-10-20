package com.ggec.earbuds.ui.ai.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AiChatViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final MemoryConversationRepository conversations = new MemoryConversationRepository();
    private final ScriptedChatGateway gateway = new ScriptedChatGateway();
    private final ManualClock clock = new ManualClock();
    private AiChatViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = newViewModel();
    }

    private AiChatViewModel newViewModel() {
        AiChatSession session = new AiChatSession(settings, conversations, gateway, clock, clock, Runnable::run);
        return new AiChatViewModel(session, new ListConversations(conversations), new LoadAiSettings(settings),
                new SelectModel(settings), new AiChatUiMapper(), new AiModelSheetMapper(), Runnable::run);
    }

    @Test
    public void publishesTheLoadedWelcomeStateOnCreation() {
        AiChatUiState state = viewModel.uiState().getValue();

        assertTrue(state.loaded());
        assertEquals(AiChatUiState.Welcome.SUGGESTIONS, state.welcome());
        assertEquals("deepseek · deepseek-chat", state.subtitle());
        assertTrue(state.composer().enabled());
        assertEquals("deepseek · deepseek-chat", state.composer().modelChip());
    }

    @Test
    public void sendingStreamsIntoTheMessageList() {
        viewModel.send("你好");
        gateway.lastListener().onReasoning("想");
        gateway.lastListener().onText("嗨");

        AiChatUiState state = viewModel.uiState().getValue();
        assertEquals(AiChatUiState.Welcome.NONE, state.welcome());
        assertTrue(state.composer().generating());
        assertEquals(2, state.messages().size());
        assertTrue(state.messages().get(0).fromUser());
        AiMessageUi reply = state.messages().get(1);
        assertEquals("嗨", reply.text());
        assertEquals("想", reply.reasoning());
        assertTrue(reply.streaming());
        assertFalse("a reply that already has text collapses its reasoning", reply.reasoningExpanded());

        gateway.lastListener().onComplete(new AiChatResult("嗨", "想", AiFinishReason.STOP, null));
        assertFalse(viewModel.uiState().getValue().composer().generating());
        assertTrue(viewModel.uiState().getValue().messages().get(1).canRegenerate());
    }

    @Test
    public void reasoningStaysOpenWhileThinkingAndTheUserCanToggleIt() {
        viewModel.send("hi");
        gateway.lastListener().onReasoning("hmm");
        AiMessageUi thinking = viewModel.uiState().getValue().messages().get(1);
        assertTrue(thinking.reasoningExpanded());

        viewModel.toggleReasoning(thinking.id());
        assertFalse(viewModel.uiState().getValue().messages().get(1).reasoningExpanded());
        gateway.lastListener().onText("answer");
        assertFalse("the user's choice sticks", viewModel.uiState().getValue().messages().get(1).reasoningExpanded());

        viewModel.toggleReasoning(thinking.id());
        assertTrue(viewModel.uiState().getValue().messages().get(1).reasoningExpanded());
        viewModel.toggleReasoning("ghost");
    }

    @Test
    public void hidingReasoningInThePreferencesRemovesTheBlock() {
        settings.update(s -> s.withPreferences(AiChatPreferences.DEFAULT.withShowReasoning(false)));
        viewModel.send("hi");
        gateway.lastListener().onReasoning("secret thoughts");
        gateway.lastListener().onText("answer");

        assertNull(viewModel.uiState().getValue().messages().get(1).reasoning());
    }

    @Test
    public void failuresAndStopsBecomeNotices() {
        viewModel.send("hi");
        gateway.lastListener().onText("part");
        gateway.lastListener().onFailure(new AiFailure(AiFailureKind.RATE_LIMITED, "slow down"));

        AiMessageUi failed = viewModel.uiState().getValue().messages().get(1);
        assertEquals(AiMessageUi.Notice.ERROR, failed.notice());
        assertEquals(AiFailureKind.RATE_LIMITED, failed.failureKind());
        assertEquals("slow down", failed.failureDetail());
        assertEquals("part", failed.text());
        assertTrue(failed.canRegenerate());

        viewModel.regenerate();
        gateway.lastListener().onText("again");
        viewModel.stop();
        assertEquals(AiMessageUi.Notice.STOPPED, viewModel.uiState().getValue().messages().get(1).notice());
    }

    @Test
    public void notReadyStatesGuideTheUser() {
        settings.update(s -> AiSettings.EMPTY);
        assertEquals(AiChatUiState.Welcome.NEEDS_PLATFORM, viewModel.uiState().getValue().welcome());
        assertFalse(viewModel.uiState().getValue().composer().enabled());
        assertEquals("", viewModel.uiState().getValue().subtitle());
        assertNull(viewModel.uiState().getValue().composer().modelChip());

        settings.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "deepseek-chat")));
        assertEquals(AiChatUiState.Welcome.NEEDS_MODEL, viewModel.uiState().getValue().welcome());
    }

    @Test
    public void draftSurvivesButIsClearedByANewConversation() {
        viewModel.setDraft("没发完的话");
        assertEquals("没发完的话", viewModel.draft());

        viewModel.newConversation();

        assertEquals("", viewModel.draft());
    }

    @Test
    public void historyIsLoadedOnDemandAndRefreshesWhileWatched() {
        viewModel.send("第一个问题");
        gateway.lastListener().onComplete(new AiChatResult("答", null, AiFinishReason.STOP, null));
        assertTrue("history is not loaded until someone watches it", viewModel.history().getValue().isEmpty());

        viewModel.watchHistory();
        List<AiHistoryItemUi> items = viewModel.history().getValue();
        assertEquals(1, items.size());
        assertEquals("第一个问题", items.get(0).title());
        assertTrue(items.get(0).current());

        clock.advance(60_000);
        viewModel.newConversation();
        viewModel.send("第二个问题");
        gateway.lastListener().onComplete(new AiChatResult("答", null, AiFinishReason.STOP, null));
        assertEquals(2, viewModel.history().getValue().size());
        assertEquals("第二个问题", viewModel.history().getValue().get(0).title());

        String firstId = viewModel.history().getValue().get(1).id();
        viewModel.deleteConversation(firstId);
        assertEquals(1, viewModel.history().getValue().size());

        viewModel.renameConversation(viewModel.history().getValue().get(0).id(), "改名了");
        assertEquals("改名了", viewModel.history().getValue().get(0).title());

        viewModel.unwatchHistory();
        viewModel.clearHistory();
        assertEquals("an unwatched list is left alone", 1, viewModel.history().getValue().size());
        viewModel.watchHistory();
        assertTrue(viewModel.history().getValue().isEmpty());
    }

    @Test
    public void openingAConversationReplacesTheMessages() {
        conversations.save(new AiConversation("old", "旧", 1, 5,
                List.of(AiMessage.user("u", "old question", 1))));
        viewModel = newViewModel();
        viewModel.newConversation();
        viewModel.send("new question");
        gateway.lastListener().onComplete(new AiChatResult("a", null, AiFinishReason.STOP, null));
        assertEquals("new question", viewModel.uiState().getValue().messages().get(0).text());

        viewModel.openConversation("old");

        assertEquals(1, viewModel.uiState().getValue().messages().size());
        assertEquals("old question", viewModel.uiState().getValue().messages().get(0).text());
    }

    @Test
    public void theModelSheetListsConfiguredPlatformsAndFlagsTheSelection() {
        settings.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "deepseek-chat", "deepseek-reasoner")
                .withSpeedResult(new AiSpeedResult("deepseek-chat", AiSpeedResult.Status.OK, 420, 900, null, null, 1)))
                .withPlatform(AiFixtures.platform("openai", "", "gpt-4o"))
                .withPlatform(AiFixtures.platform("empty", "k")));
        assertNull(viewModel.modelSheet().getValue());

        viewModel.prepareModelSheet();

        AiModelSheetUi sheet = viewModel.modelSheet().getValue();
        assertNotNull(sheet);
        assertEquals("only configured platforms that have models", 1, sheet.groups().size());
        AiModelSheetUi.Group group = sheet.groups().get(0);
        assertEquals("deepseek", group.platformId());
        assertTrue(group.rows().get(0).selected());
        assertEquals(420L, group.rows().get(0).firstTokenMillis());
        assertEquals(-1L, group.rows().get(1).firstTokenMillis());
        assertFalse(sheet.empty());
    }

    @Test
    public void choosingAModelSwitchesTheConversationModelAndToasts() {
        List<AiChatEvent> seen = new ArrayList<>();
        viewModel.events().observeForever(seen::add);

        viewModel.chooseModel("deepseek", "deepseek-reasoner", "deepseek-reasoner");

        assertEquals(new AiSelection("deepseek", "deepseek-reasoner"), settings.load().selection());
        assertEquals("deepseek · deepseek-reasoner", viewModel.uiState().getValue().subtitle());
        assertEquals(1, seen.size());
        assertEquals(R.string.ai_model_switched, seen.get(0).message());
        assertEquals("deepseek-reasoner", seen.get(0).argument());

        viewModel.chooseModel("deepseek", "ghost", "ghost");
        assertEquals(R.string.ai_error_model_unknown, seen.get(1).message());
        assertEquals(new AiSelection("deepseek", "deepseek-reasoner"), settings.load().selection());
    }

    @Test
    public void clearedViewModelsStopListeningToTheSession() {
        AiChatUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        settings.update(s -> AiSettings.EMPTY);

        assertEquals(before, viewModel.uiState().getValue());
    }
}
