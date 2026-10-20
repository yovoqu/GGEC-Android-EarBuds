package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;

import org.junit.Test;

import java.util.List;

public class AiConversationPoliciesTest {

    private final AiContextPolicy context = new AiContextPolicy();
    private final AiTitlePolicy titles = new AiTitlePolicy();
    private final AiMessageRecovery recovery = new AiMessageRecovery();
    private final AiSpeedRanking ranking = new AiSpeedRanking();

    @Test
    public void contextKeepsFinishedAndStoppedMessagesOnly() {
        AiMessage failed = assistant("a2", "half").fail(AiFailureKind.NETWORK, "x", 5);
        List<AiChatTurn> turns = context.window(List.of(
                user("u1", "hi"),
                assistant("a1", "hello").complete(AiFinishReason.STOP, null, 3),
                user("u2", "again"),
                failed,
                user("u3", "retry"),
                assistant("a3", "partial").stop(2),
                user("u4", "last")), 0);

        assertEquals(List.of(
                new AiChatTurn(AiRole.USER, "hi"),
                new AiChatTurn(AiRole.ASSISTANT, "hello"),
                new AiChatTurn(AiRole.USER, "again"),
                new AiChatTurn(AiRole.USER, "retry"),
                new AiChatTurn(AiRole.ASSISTANT, "partial"),
                new AiChatTurn(AiRole.USER, "last")), turns);
    }

    @Test
    public void contextWindowStartsOnAUserTurnAndEndsOnOne() {
        List<AiMessage> history = List.of(
                user("u1", "one"),
                assistant("a1", "two").complete(AiFinishReason.STOP, null, 0),
                user("u2", "three"),
                assistant("a2", "four").complete(AiFinishReason.STOP, null, 0),
                user("u3", "five"));

        assertEquals(List.of("three", "four", "five"), texts(context.window(history, 3)));
        assertEquals(List.of("three", "four", "five"), texts(context.window(history, 4)));
        assertEquals(List.of("five"), texts(context.window(history, 1)));
        assertEquals(5, context.window(history, 0).size());
    }

    @Test
    public void contextRefusesAHistoryThatEndsWithoutAUserTurn() {
        try {
            context.window(List.of(user("u1", "hi"),
                    assistant("a1", "yo").complete(AiFinishReason.STOP, null, 0)), 0);
            fail("a request must end with a user turn");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("user"));
        }
    }

    @Test
    public void titlesCollapseWhitespaceAndCutLongTextOnCodePoints() {
        assertEquals("耳机连不上 怎么办", titles.titleFor("  耳机连不上\n  怎么办 "));
        assertEquals("", titles.titleFor("   \n "));
        assertEquals("", titles.titleFor(null));
        String exact = "a".repeat(AiTitlePolicy.MAX_CODE_POINTS);
        assertEquals(exact, titles.titleFor(exact));
        assertEquals("a".repeat(AiTitlePolicy.MAX_CODE_POINTS) + "…", titles.titleFor(exact + "b"));
        String emoji = "😀".repeat(AiTitlePolicy.MAX_CODE_POINTS + 3);
        assertEquals("😀".repeat(AiTitlePolicy.MAX_CODE_POINTS) + "…", titles.titleFor(emoji));
    }

    @Test
    public void renamedTitlesHaveALargerLimitAndNoEllipsis() {
        assertEquals("我的耳机", titles.renamed("  我的耳机  "));
        assertEquals("x".repeat(AiTitlePolicy.MAX_RENAMED_CODE_POINTS),
                titles.renamed("x".repeat(AiTitlePolicy.MAX_RENAMED_CODE_POINTS + 10)));
        assertEquals("", titles.renamed("  "));
    }

    @Test
    public void recoveryClosesInterruptedMessages() {
        AiMessage partial = assistant("a1", "half an ans");
        AiMessage empty = AiMessage.assistantPlaceholder("a2", "m", 5);
        AiConversation interrupted = new AiConversation("c1", "t", 1, 2,
                List.of(user("u1", "q"), partial, user("u2", "q2"), empty));

        AiConversation recovered = recovery.recover(interrupted);

        assertEquals(3, recovered.messages().size());
        assertEquals(AiMessageStatus.STOPPED, recovered.messages().get(1).status());
        assertEquals("half an ans", recovered.messages().get(1).text());
        assertEquals("u2", recovered.messages().get(2).id());
        assertEquals(2L, recovered.updatedAtMillis());
    }

    @Test
    public void recoveryLeavesHealthyConversationsAlone() {
        AiConversation healthy = new AiConversation("c1", "t", 1, 2, List.of(user("u1", "q"),
                assistant("a1", "done").complete(AiFinishReason.STOP, null, 1)));
        assertSame(healthy, recovery.recover(healthy));
    }

    @Test
    public void rankingPutsFastSuccessesFirstAndKeepsFailuresLast() {
        List<AiSpeedResult> ranked = ranking.rank(List.of(
                speed("bad", AiSpeedResult.Status.FAILED, -1, 30),
                speed("b", AiSpeedResult.Status.OK, 200, 500),
                speed("a", AiSpeedResult.Status.OK, 200, 400),
                speed("c", AiSpeedResult.Status.OK, 90, 900),
                speed("slow", AiSpeedResult.Status.TIMEOUT, -1, 25_000)));

        assertEquals(List.of("c", "a", "b", "bad", "slow"), ranked.stream().map(AiSpeedResult::modelId).toList());
        assertEquals("c", ranking.fastest(ranked).orElseThrow().modelId());
        assertTrue(ranking.fastest(List.of(speed("bad", AiSpeedResult.Status.FAILED, -1, 1))).isEmpty());
        assertTrue(ranking.fastest(List.of()).isEmpty());
    }

    private static List<String> texts(List<AiChatTurn> turns) {
        return turns.stream().map(AiChatTurn::text).toList();
    }

    private static AiMessage user(String id, String text) {
        return AiMessage.user(id, text, 1);
    }

    private static AiMessage assistant(String id, String text) {
        return AiMessage.assistantPlaceholder(id, "m", 1).appendText(text);
    }

    private static AiSpeedResult speed(String model, AiSpeedResult.Status status, long first, long total) {
        return new AiSpeedResult(model, status, first, total, null, null, 0);
    }
}
