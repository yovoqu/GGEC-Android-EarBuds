package com.ggec.earbuds.domain.ai.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;
import com.ggec.earbuds.domain.ai.usecase.RecordUsage;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.port.AiChatListener;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AiChatSessionTest {

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final MemoryConversationRepository conversations = new MemoryConversationRepository();
    private final ScriptedChatGateway gateway = new ScriptedChatGateway();
    private final ManualClock clock = new ManualClock();
    private final List<AiChatState> published = new ArrayList<>();
    private final MemoryUsageStore usageStore = new MemoryUsageStore();
    private AiChatSession session;

    @Before
    public void setUp() {
        session = new AiChatSession(settings, conversations, gateway, clock, clock,
                new RecordUsage(usageStore, clock), Runnable::run);
        session.observe(published::add);
    }

    @Test
    public void notLoadedUntilStarted() {
        assertFalse(session.state().loaded());
        session.start();
        assertTrue(session.state().loaded());
        assertTrue(session.state().conversation().empty());
        assertEquals(AiChatState.Readiness.READY, session.state().readiness());
        assertEquals("deepseek-chat", session.state().model().model().id());
    }

    @Test
    public void readinessSeparatesMissingPlatformFromMissingModel() {
        settings.update(s -> AiSettings.EMPTY);
        session.start();
        assertEquals(AiChatState.Readiness.NO_PLATFORM, session.state().readiness());

        settings.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "deepseek-chat")));
        assertEquals("a platform without a chosen model is a different problem",
                AiChatState.Readiness.NO_MODEL, session.state().readiness());

        settings.update(s -> AiFixtures.readySettings());
        assertEquals(AiChatState.Readiness.READY, session.state().readiness());
        assertTrue(session.state().ready());

        settings.update(s -> s.withoutPlatform("deepseek"));
        assertEquals(AiChatState.Readiness.NO_PLATFORM, session.state().readiness());
        assertNull(session.state().model());
    }

    @Test
    public void sendStreamsReasoningAndTextThenCompletes() {
        session.start();
        session.send("  你好  ");

        AiChatState sending = session.state();
        assertTrue(sending.generating());
        assertEquals(2, sending.conversation().messages().size());
        assertEquals("你好", sending.conversation().messages().get(0).text());
        assertEquals("你好", sending.conversation().title());
        assertEquals(AiMessageStatus.STREAMING, assistant().status());
        assertEquals("deepseek-chat", assistant().modelLabel());
        assertEquals(1, conversations.saves);

        var request = gateway.lastRequest();
        assertEquals("deepseek", request.platform().id());
        assertEquals("deepseek-chat", request.modelId());
        assertEquals(AiChatPreferences.DEFAULT_SYSTEM_PROMPT, request.systemPrompt());
        assertEquals(List.of(new AiChatTurn(AiRole.USER, "你好")), request.turns());

        AiChatListener listener = gateway.lastListener();
        listener.onReasoning("想");
        listener.onReasoning("一下");
        listener.onText("你");
        listener.onText("好！");
        assertEquals("想一下", assistant().reasoning());
        assertEquals("你好！", assistant().text());
        assertTrue(session.state().generating());

        clock.advance(1_500);
        listener.onComplete(new AiChatResult("你好！", "想一下", AiFinishReason.STOP, AiUsage.ofTokens(3, 5)));

        AiMessage done = assistant();
        assertEquals(AiMessageStatus.COMPLETE, done.status());
        assertEquals(1_500L, done.elapsedMillis());
        assertEquals(8, done.usage().totalTokens());
        assertFalse(session.state().generating());
        assertEquals(AiMessageStatus.COMPLETE, conversations.stored.values().iterator().next().messages().get(1).status());
    }

    @Test
    public void everyRequestLeavesOneUsageRecord() {
        session.start();
        clock.advance(1_000);

        session.send("hi");
        gateway.lastListener().onComplete(new AiChatResult("answer", null, AiFinishReason.STOP,
                new AiUsage(120, 40, 160, new java.math.BigDecimal("0.0021"), null, null)));

        assertEquals(1, usageStore.size());
        AiUsageRecord record = usageStore.last();
        assertEquals(AiUsageStatus.SUCCESS, record.status());
        assertEquals(120, record.inputTokens());
        assertEquals(40, record.outputTokens());
        assertEquals(new java.math.BigDecimal("0.0021"), record.cost());
        assertEquals("deepseek", record.platformId());
        assertEquals("deepseek-chat", record.modelId());
        assertEquals(session.state().conversation().id(), record.conversationId());

        // 用户停止算"取消"，不算失败：否则错误率会被用户自己按的停止污染。
        session.send("again");
        gateway.lastListener().onText("部分");
        clock.advance(500);
        session.stop();
        assertEquals(2, usageStore.size());
        assertEquals(AiUsageStatus.CANCELLED, usageStore.last().status());
    }

    @Test
    public void failedRequestIsRecordedWithItsKind() {
        session.start();
        session.send("hi");
        gateway.lastListener().onFailure(new AiFailure(AiFailureKind.AUTHENTICATION, "bad key"));

        assertEquals(1, usageStore.size());
        assertEquals(AiUsageStatus.FAILED, usageStore.last().status());
        assertEquals("AUTHENTICATION", usageStore.last().failureReason());
    }

    @Test
    public void completionTextFillsInWhenNoDeltaArrived() {
        session.start();
        session.send("hi");

        gateway.lastListener().onComplete(new AiChatResult("whole answer", "why", AiFinishReason.LENGTH, null));

        assertEquals("whole answer", assistant().text());
        assertEquals("why", assistant().reasoning());
        assertEquals(AiFinishReason.LENGTH, assistant().finishReason());
    }

    @Test
    public void blankAndOverlappingSendsAreIgnored() {
        session.start();
        session.send("   ");
        assertTrue(session.state().conversation().empty());
        assertEquals(0, gateway.requests.size());

        session.send("first");
        session.send("second while busy");
        assertEquals(1, gateway.requests.size());
        assertEquals(2, session.state().conversation().messages().size());
    }

    @Test
    public void sendingWithoutAUsableModelDoesNothing() {
        settings.update(s -> s.withSelection(null));
        session.start();

        session.send("hello");

        assertEquals(0, gateway.requests.size());
        assertTrue(session.state().conversation().empty());
        assertEquals(AiChatState.Readiness.NO_MODEL, session.state().readiness());
    }

    @Test
    public void stopKeepsWhatWasReceivedAndIgnoresLateCallbacks() {
        session.start();
        session.send("tell me");
        AiChatListener listener = gateway.lastListener();
        listener.onText("Hel");

        clock.advance(400);
        session.stop();

        assertTrue(gateway.lastHandle().cancelled);
        assertFalse(session.state().generating());
        assertEquals(AiMessageStatus.STOPPED, assistant().status());
        assertEquals("Hel", assistant().text());
        assertEquals(400L, assistant().elapsedMillis());

        listener.onText("lo");
        listener.onComplete(new AiChatResult("Hello", null, AiFinishReason.STOP, null));
        assertEquals("a late callback must not resurrect the reply", "Hel", assistant().text());
        assertEquals(AiMessageStatus.STOPPED, assistant().status());
        assertEquals(AiMessageStatus.STOPPED, savedAssistant().status());
    }

    @Test
    public void stopBeforeAnyTokenRemovesTheEmptyReply() {
        session.start();
        session.send("tell me");

        session.stop();

        assertEquals(1, session.state().conversation().messages().size());
        assertTrue(session.state().conversation().messages().get(0).user());
        assertFalse(session.state().generating());
    }

    @Test
    public void failureKeepsThePartialReplyAndRecordsWhy() {
        session.start();
        session.send("tell me");
        gateway.lastListener().onText("Hel");

        gateway.lastListener().onFailure(new AiFailure(AiFailureKind.NETWORK, "connection reset"));

        AiMessage failed = assistant();
        assertEquals(AiMessageStatus.ERROR, failed.status());
        assertEquals("Hel", failed.text());
        assertEquals(AiFailureKind.NETWORK, failed.failure().kind());
        assertEquals("connection reset", failed.failure().detail());
        assertFalse(session.state().generating());
        assertEquals(AiMessageStatus.ERROR, savedAssistant().status());
    }

    @Test
    public void regenerateRetriesAfterAFailureWithoutSendingTheBrokenReply() {
        session.start();
        session.send("question");
        gateway.lastListener().onText("part");
        gateway.lastListener().onFailure(new AiFailure(AiFailureKind.SERVER, "503"));

        session.regenerate();

        assertEquals(2, gateway.requests.size());
        assertEquals(List.of(new AiChatTurn(AiRole.USER, "question")), gateway.lastRequest().turns());
        assertEquals(2, session.state().conversation().messages().size());
        assertTrue(session.state().generating());

        gateway.lastListener().onComplete(new AiChatResult("answer", null, AiFinishReason.STOP, null));
        assertEquals(List.of("question", "answer"),
                session.state().conversation().messages().stream().map(AiMessage::text).toList());
    }

    @Test
    public void regenerateAfterACompleteReplyAsksAgain() {
        session.start();
        session.send("question");
        gateway.lastListener().onComplete(new AiChatResult("first", null, AiFinishReason.STOP, null));

        session.regenerate();
        gateway.lastListener().onComplete(new AiChatResult("second", null, AiFinishReason.STOP, null));

        assertEquals(List.of("question", "second"),
                session.state().conversation().messages().stream().map(AiMessage::text).toList());
    }

    @Test
    public void regenerateIsIgnoredWhileGeneratingOrEmpty() {
        session.start();
        session.regenerate();
        assertEquals(0, gateway.requests.size());

        session.send("question");
        session.regenerate();
        assertEquals(1, gateway.requests.size());
    }

    @Test
    public void aGatewayThatRefusesTheRequestBecomesAFailedReply() {
        gateway.failOnStream = new AiFailure(AiFailureKind.BAD_REQUEST, "unsupported protocol");
        session.start();

        session.send("hi");

        assertFalse(session.state().generating());
        assertEquals(AiMessageStatus.ERROR, assistant().status());
        assertEquals(AiFailureKind.BAD_REQUEST, assistant().failure().kind());
        assertEquals(1, conversations.stored.values().iterator().next().messages().stream()
                .filter(m -> !m.user()).count());
    }

    @Test
    public void preferencesShapeTheRequestAndTheContextWindow() {
        settings.update(s -> s.withPreferences(new AiChatPreferences("sys", 0.5, null, 100, 3, true)));
        session.start();
        for (String question : new String[] {"q1", "q2"}) {
            session.send(question);
            gateway.lastListener().onComplete(new AiChatResult("a-" + question, null, AiFinishReason.STOP, null));
        }

        session.send("q3");

        var request = gateway.lastRequest();
        assertEquals("sys", request.systemPrompt());
        assertEquals(Double.valueOf(0.5), request.temperature());
        assertEquals(Integer.valueOf(100), request.maxOutputTokens());
        assertEquals(List.of("q2", "a-q2", "q3"), request.turns().stream().map(AiChatTurn::text).toList());
    }

    @Test
    public void aBlankSystemPromptIsNotSent() {
        settings.update(s -> s.withPreferences(AiChatPreferences.DEFAULT.withSystemPrompt("   ")));
        session.start();

        session.send("q");

        assertNull(gateway.lastRequest().systemPrompt());
    }

    @Test
    public void newConversationStopsTheRunAndKeepsTheOldOneSaved() {
        session.start();
        session.send("tell me");
        gateway.lastListener().onText("Hel");
        String oldId = session.state().conversation().id();

        session.newConversation();

        assertTrue(gateway.lastHandle().cancelled);
        assertNotEquals(oldId, session.state().conversation().id());
        assertTrue(session.state().conversation().empty());
        assertFalse(session.state().generating());
        AiConversation saved = conversations.stored.get(oldId);
        assertEquals(AiMessageStatus.STOPPED, saved.messages().get(1).status());
        assertEquals("Hel", saved.messages().get(1).text());
    }

    @Test
    public void startRestoresTheLatestConversationAndClosesInterruptedReplies() {
        conversations.save(conversation("old", 100, AiMessage.user("u", "old question", 1)));
        AiMessage interrupted = AiMessage.assistantPlaceholder("a", "m", 2).appendText("half");
        conversations.save(conversation("recent", 500, AiMessage.user("u2", "recent question", 1), interrupted));

        session.start();

        assertEquals("recent", session.state().conversation().id());
        AiMessage restored = session.state().conversation().messages().get(1);
        assertEquals(AiMessageStatus.STOPPED, restored.status());
        assertEquals("half", restored.text());
        assertEquals(AiMessageStatus.STOPPED, conversations.stored.get("recent").messages().get(1).status());
    }

    @Test
    public void openSwitchesConversationsAndIgnoresUnknownIds() {
        conversations.save(conversation("a", 100, AiMessage.user("u", "alpha", 1)));
        conversations.save(conversation("b", 200, AiMessage.user("u2", "beta", 1)));
        session.start();
        assertEquals("b", session.state().conversation().id());

        session.open("a");
        assertEquals("a", session.state().conversation().id());

        session.open("ghost");
        assertEquals("a", session.state().conversation().id());
    }

    @Test
    public void deletingAMessageRemovesTheQuestionTogetherWithItsAnswer() {
        session.start();
        session.send("q1");
        gateway.lastListener().onComplete(new AiChatResult("a1", null, AiFinishReason.STOP, null));
        session.send("q2");
        gateway.lastListener().onComplete(new AiChatResult("a2", null, AiFinishReason.STOP, null));
        String firstQuestion = session.state().conversation().messages().get(0).id();

        session.deleteMessage(firstQuestion);

        assertEquals(List.of("q2", "a2"),
                session.state().conversation().messages().stream().map(AiMessage::text).toList());
        String lastAnswer = session.state().conversation().messages().get(1).id();
        session.deleteMessage(lastAnswer);
        assertEquals(List.of("q2"), session.state().conversation().messages().stream().map(AiMessage::text).toList());
    }

    @Test
    public void deletingEverythingDropsTheConversationFromStorage() {
        session.start();
        session.send("q1");
        gateway.lastListener().onComplete(new AiChatResult("a1", null, AiFinishReason.STOP, null));
        String id = session.state().conversation().id();
        assertTrue(conversations.stored.containsKey(id));

        session.deleteMessage(session.state().conversation().messages().get(0).id());

        assertTrue(session.state().conversation().empty());
        assertFalse(conversations.stored.containsKey(id));
    }

    @Test
    public void messagesCannotBeDeletedWhileGenerating() {
        session.start();
        session.send("q1");
        String userId = session.state().conversation().messages().get(0).id();

        session.deleteMessage(userId);

        assertEquals(2, session.state().conversation().messages().size());
    }

    @Test
    public void deletingTheCurrentConversationStartsANewOneButOthersLeaveItAlone() {
        conversations.save(conversation("a", 100, AiMessage.user("u", "alpha", 1)));
        conversations.save(conversation("b", 200, AiMessage.user("u2", "beta", 1)));
        session.start();

        session.deleteConversation("a");
        assertEquals("b", session.state().conversation().id());
        assertFalse(conversations.stored.containsKey("a"));

        session.deleteConversation("b");
        assertTrue(session.state().conversation().empty());
        assertNotEquals("b", session.state().conversation().id());
        assertTrue(conversations.stored.isEmpty());
    }

    @Test
    public void clearAllStopsTheRunAndEmptiesHistory() {
        conversations.save(conversation("a", 100, AiMessage.user("u", "alpha", 1)));
        session.start();
        session.send("tell me");

        session.clearAll();

        assertTrue(gateway.lastHandle().cancelled);
        assertTrue(conversations.stored.isEmpty());
        assertTrue(session.state().conversation().empty());
        assertFalse(session.state().generating());
    }

    @Test
    public void renamingWorksForTheCurrentAndForStoredConversations() {
        conversations.save(conversation("a", 100, AiMessage.user("u", "alpha", 1)));
        conversations.save(conversation("b", 200, AiMessage.user("u2", "beta", 1)));
        session.start();

        session.rename("b", "  新标题  ");
        assertEquals("新标题", session.state().conversation().title());
        assertEquals("新标题", conversations.stored.get("b").title());

        session.rename("a", "旧的");
        assertEquals("旧的", conversations.stored.get("a").title());
        assertEquals("新标题", session.state().conversation().title());

        session.rename("a", "   ");
        assertEquals("旧的", conversations.stored.get("a").title());
    }

    @Test
    public void storageFailuresAreReportedWithoutInterruptingTheChat() {
        conversations.failSaves = true;
        session.start();

        session.send("hello");
        gateway.lastListener().onText("hi");

        assertTrue(session.state().storageFailed());
        assertEquals("hi", assistant().text());
        assertTrue(session.state().generating());

        conversations.failSaves = false;
        gateway.lastListener().onComplete(new AiChatResult("hi", null, AiFinishReason.STOP, null));
        assertFalse(session.state().storageFailed());
    }

    @Test
    public void observersReceiveTheCurrentStateAndCanUnsubscribe() {
        int before = published.size();
        assertTrue("subscribing replays the current state", before >= 1);

        List<AiChatState> second = new ArrayList<>();
        Subscription subscription = session.observe(second::add);
        assertEquals(1, second.size());

        subscription.cancel();
        session.start();
        assertEquals(1, second.size());
        assertTrue(published.size() > before);
    }

    @Test
    public void aFailingObserverDoesNotStarveTheOthers() {
        session.observe(state -> {
            throw new IllegalStateException("boom");
        });
        int before = published.size();

        session.start();

        assertTrue(published.size() > before);
    }

    private AiMessage assistant() {
        List<AiMessage> messages = session.state().conversation().messages();
        return messages.get(messages.size() - 1);
    }

    private AiMessage savedAssistant() {
        AiConversation saved = conversations.stored.get(session.state().conversation().id());
        return saved.messages().get(saved.messages().size() - 1);
    }

    private static AiConversation conversation(String id, long updatedAt, AiMessage... messages) {
        return new AiConversation(id, "", 1, updatedAt, List.of(messages));
    }
}
