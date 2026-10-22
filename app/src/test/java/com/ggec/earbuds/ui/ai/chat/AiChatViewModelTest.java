package com.ggec.earbuds.ui.ai.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

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
        return new AiChatViewModel(session, new AiChatUiMapper());
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
    public void clearedViewModelsStopListeningToTheSession() {
        AiChatUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        settings.update(s -> AiSettings.EMPTY);

        assertEquals(before, viewModel.uiState().getValue());
    }
}
