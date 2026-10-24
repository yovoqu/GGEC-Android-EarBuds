package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiUsage;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 对话页与历史面板的纯规则：速度、发送条件、失败操作、分组、搜索。 */
public class AiChatHistoryRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long NOW = at(2025, 10, 24, 15, 0);

    // ------------------------------------------------------------------ SPD-A-02/03

    @Test
    public void tokenEstimateCountsCjkPerCharacterAndOtherTextPerFourCharacters() {
        assertEquals(4, AiSpeedPolicy.estimateTokens("你好世界"));
        assertEquals(2, AiSpeedPolicy.estimateTokens("abcdefgh"));
        assertEquals(1, AiSpeedPolicy.estimateTokens("a"));
        assertEquals(0, AiSpeedPolicy.estimateTokens(""));
        assertEquals(0, AiSpeedPolicy.estimateTokens(null));
    }

    @Test
    public void liveSpeedWaitsHalfASecond() {
        assertNull(AiSpeedPolicy.live(10, 400));
        AiSpeedPolicy.Speed speed = AiSpeedPolicy.live(10, 1_000);
        assertEquals(10, speed.tokensPerSecond());
        assertTrue(speed.estimated());
    }

    @Test
    public void averageSpeedPrefersReportedTokensAndNeedsEnoughData() {
        AiMessage reported = answer("x".repeat(40)).complete(AiFinishReason.STOP, AiUsage.ofTokens(5, 410), 10_800);
        AiSpeedPolicy.Speed exact = AiSpeedPolicy.average(reported);
        assertEquals(41, exact.tokensPerSecond());
        assertFalse(exact.estimated());

        AiMessage unreported = answer("abcdefgh".repeat(50)).complete(AiFinishReason.STOP, null, 10_800);
        AiSpeedPolicy.Speed estimate = AiSpeedPolicy.average(unreported);
        assertEquals(10, estimate.tokensPerSecond());
        assertTrue(estimate.estimated());

        assertNull("1 token 不显示", AiSpeedPolicy.average(answer("hi").complete(AiFinishReason.STOP,
                AiUsage.ofTokens(1, 1), 10_800)));
        assertNull("生成中不算平均", AiSpeedPolicy.average(answer("hello world")));
        assertNull("没有首字", AiSpeedPolicy.average(AiMessage.assistantPlaceholder("a", "m", 0)
                .complete(AiFinishReason.STOP, AiUsage.ofTokens(1, 100), 900)));
    }

    // ------------------------------------------------------------------ CHAT-04

    @Test
    public void sendBlockersExplainEverythingButAnEmptyInput() {
        assertNull(AiSendPolicy.evaluate("hi", true, true, true, false));
        assertEquals(AiSendPolicy.Blocker.EMPTY, AiSendPolicy.evaluate("  ", true, true, true, false));
        assertEquals(AiSendPolicy.Blocker.NO_MODEL, AiSendPolicy.evaluate("hi", false, true, true, false));
        assertEquals(AiSendPolicy.Blocker.NO_API_KEY, AiSendPolicy.evaluate("hi", true, false, true, false));
        assertEquals(AiSendPolicy.Blocker.OFFLINE, AiSendPolicy.evaluate("hi", true, true, false, false));
        assertEquals(AiSendPolicy.Blocker.GENERATING, AiSendPolicy.evaluate("hi", true, true, true, true));
        assertFalse(AiSendPolicy.explains(AiSendPolicy.Blocker.EMPTY));
        assertFalse(AiSendPolicy.explains(AiSendPolicy.Blocker.GENERATING));
        assertTrue(AiSendPolicy.explains(AiSendPolicy.Blocker.NO_MODEL));
        assertTrue(AiSendPolicy.explains(AiSendPolicy.Blocker.NO_API_KEY));
        assertTrue(AiSendPolicy.explains(AiSendPolicy.Blocker.OFFLINE));
    }

    // ------------------------------------------------------------------ CHAT-10

    @Test
    public void failureCardsOfferRetryForTransientAndSettingsForConfigurationProblems() {
        for (AiFailureKind kind : List.of(AiFailureKind.NETWORK, AiFailureKind.TIMEOUT,
                AiFailureKind.RATE_LIMITED, AiFailureKind.SERVER)) {
            assertEquals(kind.name(), new AiFailureActionPolicy.Actions(true, false), AiFailureActionPolicy.of(kind));
        }
        for (AiFailureKind kind : List.of(AiFailureKind.AUTHENTICATION, AiFailureKind.PERMISSION,
                AiFailureKind.NOT_FOUND, AiFailureKind.BAD_REQUEST, AiFailureKind.QUOTA, AiFailureKind.PROTOCOL)) {
            assertEquals(kind.name(), new AiFailureActionPolicy.Actions(false, true), AiFailureActionPolicy.of(kind));
        }
        assertEquals(new AiFailureActionPolicy.Actions(true, true), AiFailureActionPolicy.of(AiFailureKind.UNKNOWN));
        for (AiFailureKind kind : AiFailureKind.values()) {
            AiFailureActionPolicy.Actions actions = AiFailureActionPolicy.of(kind);
            assertTrue(kind.name(), actions.retry() || actions.settings());
        }
    }

    // ------------------------------------------------------------------ HIS-02

    @Test
    public void groupsUseLocalCalendarDays() {
        assertEquals(AiHistoryGrouping.Bucket.TODAY, bucket(at(2025, 10, 24, 0, 0)));
        assertEquals(AiHistoryGrouping.Bucket.YESTERDAY, bucket(at(2025, 10, 23, 23, 59)));
        assertEquals(AiHistoryGrouping.Bucket.YESTERDAY, bucket(at(2025, 10, 23, 0, 0)));
        assertEquals(AiHistoryGrouping.Bucket.LAST_7_DAYS, bucket(at(2025, 10, 22, 23, 59)));
        assertEquals(AiHistoryGrouping.Bucket.LAST_7_DAYS, bucket(at(2025, 10, 18, 0, 0)));
        assertEquals("7 天前 00:00", AiHistoryGrouping.Bucket.LAST_30_DAYS, bucket(at(2025, 10, 17, 0, 0)));
        assertEquals(AiHistoryGrouping.Bucket.LAST_30_DAYS, bucket(at(2025, 9, 25, 0, 0)));
        assertEquals(AiHistoryGrouping.Bucket.EARLIER, bucket(at(2025, 9, 24, 23, 59)));
        assertEquals("时钟回拨算今天", AiHistoryGrouping.Bucket.TODAY, bucket(NOW + 3_600_000));
    }

    @Test
    public void emptyGroupsAreSkippedAndItemsAreNewestFirst() {
        List<AiHistoryGrouping.Group> groups = AiHistoryGrouping.group(List.of(
                summary("old", at(2025, 1, 1, 9, 0)),
                summary("t1", at(2025, 10, 24, 9, 0)),
                summary("t2", at(2025, 10, 24, 14, 0))), NOW, ZONE);

        assertEquals(2, groups.size());
        assertEquals(AiHistoryGrouping.Bucket.TODAY, groups.get(0).bucket());
        assertEquals(List.of("t2", "t1"), groups.get(0).items().stream().map(AiConversationSummary::id).toList());
        assertEquals(AiHistoryGrouping.Bucket.EARLIER, groups.get(1).bucket());
    }

    // ------------------------------------------------------------------ HIS-04

    @Test
    public void searchHitsBodyTextAndCutsASnippetAroundTheHit() {
        String body = "前面有很多很多很多很多很多很多很多很多的文字，然后提到降噪模式怎么打开，后面还有很多很多很多很多很多很多的文字";
        AiConversation conversation = new AiConversation("c", "耳机问题", 0, 0, List.of(
                AiMessage.user("u", "**降噪**怎么开", 0),
                answer(body).complete(AiFinishReason.STOP, null, 10)));

        AiHistorySearch.Match match = AiHistorySearch.match(conversation, " 降噪模式 ");
        assertFalse(match.titleHit());
        assertTrue(match.snippet().contains("降噪模式"));
        assertTrue(match.snippet().startsWith("…"));
        assertTrue(match.snippet().endsWith("…"));
        assertEquals("降噪模式", match.snippet().substring(match.hitStart(), match.hitStart() + match.hitLength()));

        AiHistorySearch.Match markdown = AiHistorySearch.match(conversation, "降噪怎么");
        assertEquals("降噪怎么开", markdown.snippet());

        AiHistorySearch.Match title = AiHistorySearch.match(conversation, "耳机问题");
        assertTrue(title.titleHit());
        assertNull(title.snippet());

        assertNull(AiHistorySearch.match(conversation, "蓝牙"));
        assertNull(AiHistorySearch.match(conversation, "  "));
    }

    @Test
    public void searchIgnoresCase() {
        AiConversation conversation = new AiConversation("c", "Tips", 0, 0, List.of(
                AiMessage.user("u", "Use GPT-4o please", 0)));
        assertTrue(AiHistorySearch.match(conversation, "gpt-4O").snippet().contains("GPT-4o"));
        assertTrue(AiHistorySearch.match(conversation, "TIPS").titleHit());
    }

    // ------------------------------------------------------------------ 工具

    private static AiMessage answer(String text) {
        return AiMessage.assistantPlaceholder("a", "m", 0).withFirstToken(800).appendText(text);
    }

    private static AiHistoryGrouping.Bucket bucket(long time) {
        return AiHistoryGrouping.bucket(time, NOW, ZONE);
    }

    private static AiConversationSummary summary(String id, long updated) {
        return new AiConversationSummary(id, id, updated, 2, "");
    }

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ZONE).toInstant().toEpochMilli();
    }
}
