package com.ggec.earbuds.ui.ai.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 会话摘要 → 历史行：截断长度、时刻的三档、分组、当前会话标记（页面规格 §2.2）。
 *
 * <p>全部是纯函数断言，不碰视图也不碰真实时钟：{@code now} 由参数给定，时区固定成
 * {@code Asia/Shanghai}，否则「今天」「昨天」的边界会随机器设置漂。</p>
 */
public class AiHistoryUiMapperTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AiHistoryUiMapper mapper = new AiHistoryUiMapper();

    // ---- 标题：≤16 码点 ----

    @Test
    public void blankTitleFallsBackToAPlaceholder() {
        AiHistoryText title = AiHistoryUiMapper.title("   ");

        assertEquals(AiHistoryText.of(R.string.ai_history_title_untitled), title);
        assertFalse(title.isRaw());
    }

    @Test
    public void aShortTitleIsShownVerbatim() {
        assertEquals(AiHistoryText.raw("耳机连不上"), AiHistoryUiMapper.title("  耳机连不上  "));
    }

    @Test
    public void aTitleAtTheLimitIsLeftAlone() {
        String exact = "长".repeat(AiHistoryUiMapper.TITLE_MAX_CODE_POINTS);

        assertEquals(AiHistoryText.raw(exact), AiHistoryUiMapper.title(exact));
    }

    /** 超长标题在 Mapper 里就截断：视图上还有单行省略，两道保险保证时刻不会掉到第二行。 */
    @Test
    public void aLongTitleIsTruncatedToTheLimit() {
        String longTitle = "耳机连不上手机怎么办".repeat(4);

        AiHistoryText title = AiHistoryUiMapper.title(longTitle);

        assertEquals(AiHistoryUiMapper.TITLE_MAX_CODE_POINTS + 1,
                title.raw().codePointCount(0, title.raw().length()));
        assertTrue(title.raw().endsWith("…"));
    }

    // ---- 摘要：≤12 码点，空则整行不出现 ----

    /** 没有摘要时返回 null，面板据此把行高从 72dp 收回到 56dp，而不是显示「还没有内容」。 */
    @Test
    public void blankPreviewHasNoLine() {
        assertNull(AiHistoryUiMapper.preview(null));
        assertNull(AiHistoryUiMapper.preview(" \n "));
    }

    @Test
    public void aPreviewIsFlattenedToOneLine() {
        assertEquals(AiHistoryText.raw("第一行 第二行"), AiHistoryUiMapper.preview("第一行\n第二行"));
    }

    @Test
    public void aPreviewAtTheLimitIsLeftAlone() {
        String exact = "短".repeat(AiHistoryUiMapper.PREVIEW_MAX_CODE_POINTS);

        assertEquals(AiHistoryText.raw(exact), AiHistoryUiMapper.preview(exact));
    }

    @Test
    public void aLongPreviewIsTruncatedToTheLimit() {
        String longPreview = "怎么延长耳机的续航时间？".repeat(8);

        AiHistoryText preview = AiHistoryUiMapper.preview(longPreview);

        assertEquals(AiHistoryUiMapper.PREVIEW_MAX_CODE_POINTS + 1,
                preview.raw().codePointCount(0, preview.raw().length()));
        assertTrue(preview.raw().endsWith("…"));
    }

    // ---- 分组 + 时刻：同一个纯函数一次算完（今天 / 昨天 / 3 天前 / 跨年） ----

    /** 今天：分组「今天」，时刻只给 HH:mm——"今天"由分组标题说，时刻里不再重复。 */
    @Test
    public void todayIsGroupedTodayWithAClockTime() {
        long now = at(2026, 10, 22, 15, 0);

        AiHistoryUiMapper.When midnight = AiHistoryUiMapper.when(now, at(2026, 10, 22, 0, 10), ZONE);
        assertEquals(AiHistoryItemUi.Group.TODAY, midnight.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_clock, "00:10"), midnight.label());

        AiHistoryUiMapper.When justNow = AiHistoryUiMapper.when(now, now - 30_000L, ZONE);
        assertEquals(AiHistoryItemUi.Group.TODAY, justNow.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_clock, "14:59"), justNow.label());
    }

    /** 昨天：分组「昨天」，时刻「1 天前」。分组负责"哪一天"，时刻负责"精确到天"。 */
    @Test
    public void yesterdayIsGroupedYesterdayWithOneDayAgo() {
        long now = at(2026, 10, 22, 15, 0);

        AiHistoryUiMapper.When yesterday = AiHistoryUiMapper.when(now, at(2026, 10, 21, 23, 55), ZONE);

        assertEquals(AiHistoryItemUi.Group.YESTERDAY, yesterday.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_days, 1), yesterday.label());
    }

    /** 3 天前：已经不在「昨天」，但还没到给日期的程度——仍用「N 天前」。 */
    @Test
    public void threeDaysAgoStaysRelative() {
        long now = at(2026, 10, 22, 15, 0);

        AiHistoryUiMapper.When threeDays = AiHistoryUiMapper.when(now, at(2026, 10, 19, 9, 0), ZONE);

        assertEquals(AiHistoryItemUi.Group.EARLIER, threeDays.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_days, 3), threeDays.label());
    }

    /**
     * 跨年：自然日差按日历算，不会被年份边界打断；过了 7 天才换成日期，
     * 而且写记录自己的月日（12 月 20 日），不写"去年"。
     */
    @Test
    public void acrossNewYearCountsDaysNotDates() {
        long now = at(2026, 1, 2, 15, 0);

        AiHistoryUiMapper.When twoDays = AiHistoryUiMapper.when(now, at(2025, 12, 31, 9, 0), ZONE);
        assertEquals(AiHistoryItemUi.Group.EARLIER, twoDays.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_days, 2), twoDays.label());

        AiHistoryUiMapper.When lastYear = AiHistoryUiMapper.when(now, at(2025, 12, 20, 9, 0), ZONE);
        assertEquals(AiHistoryItemUi.Group.EARLIER, lastYear.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_date, 12, 20), lastYear.label());
    }

    /** 第 7 天正好换档：6 天前还数天，7 天前开始给日期。 */
    @Test
    public void theSeventhDaySwitchesToADate() {
        long now = at(2026, 10, 22, 15, 0);

        assertEquals(AiHistoryText.of(R.string.ai_history_time_days, 6),
                AiHistoryUiMapper.when(now, at(2026, 10, 16, 9, 0), ZONE).label());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_date, 10, 15),
                AiHistoryUiMapper.when(now, at(2026, 10, 15, 9, 0), ZONE).label());
    }

    /** 设备时间被改到过去时记录会"来自未来"：按今天处理，给它的时刻。 */
    @Test
    public void aFutureTimestampCountsAsToday() {
        long now = at(2026, 10, 22, 15, 0);

        AiHistoryUiMapper.When future = AiHistoryUiMapper.when(now, now + 10 * 60_000L, ZONE);

        assertEquals(AiHistoryItemUi.Group.TODAY, future.group());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_clock, "15:10"), future.label());
    }

    // ---- 整张列表 ----

    @Test
    public void newestFirstAndTheOpenConversationIsMarked() {
        List<AiConversationSummary> summaries = List.of(
                new AiConversationSummary("a", "旧的", at(2026, 10, 20, 9, 0), 2, "旧摘要"),
                new AiConversationSummary("b", "", at(2026, 10, 22, 14, 0), 4, ""));

        List<AiHistoryItemUi> rows = mapper.map(summaries, "a", at(2026, 10, 22, 15, 0), ZONE);

        assertEquals(2, rows.size());
        assertEquals("b", rows.get(0).id());
        assertEquals("a", rows.get(1).id());
        assertFalse(rows.get(0).current());
        assertTrue(rows.get(1).current());
        // 没名字、没摘要：标题走兜底，摘要整行不存在。
        assertEquals(AiHistoryText.of(R.string.ai_history_title_untitled), rows.get(0).title());
        assertNull(rows.get(0).preview());
        assertEquals(AiHistoryText.of(R.string.ai_history_time_clock, "14:00"), rows.get(0).time());
        assertEquals(AiHistoryItemUi.Group.TODAY, rows.get(0).group());
        assertEquals(AiHistoryItemUi.Group.EARLIER, rows.get(1).group());
    }

    @Test
    public void nothingIsMarkedWhenTheOpenConversationIsNotInTheList() {
        List<AiConversationSummary> summaries = List.of(
                new AiConversationSummary("a", "旧的", at(2026, 10, 20, 9, 0), 2, "旧摘要"));

        List<AiHistoryItemUi> rows = mapper.map(summaries, "not-yet-saved", at(2026, 10, 22, 15, 0), ZONE);

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).current());
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }
}
