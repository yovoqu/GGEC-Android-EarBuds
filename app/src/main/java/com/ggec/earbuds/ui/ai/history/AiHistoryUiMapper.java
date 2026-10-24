package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 会话摘要 → 历史面板的一行。纯函数：不碰视图、不碰资源、不读真实时钟（时间由调用方给）。
 *
 * <p>这一层的取舍，全部对应页面规格 §2.2 与组件规格 §2.2 的验收项：</p>
 * <ul>
 *   <li><b>标题 ≤ 16 码点、摘要 ≤ 12 码点</b>：15sp 中文在本机行宽下扣掉勾、时刻与「⋯」只剩十余字，
 *       超过就折行，而时刻是挂在标题行右端的——一折行时刻就掉到第二行中间（上一版实测过）。
 *       所以长度在 Mapper 里就截断，视图上再单行省略一次，两道保险。</li>
 *   <li><b>没有摘要就不占一行</b>：返回 {@code null} 而不是「还没有内容」占位，
 *       面板据此把行高从 72dp 收回到 56dp（规格 §2.2）。</li>
 *   <li><b>时刻</b>：今天给 {@code HH:mm}，7 天内给「N 天前」，更早给「M月d日」。
 *       未来时间（设备时钟被改过）按今天处理，不会显示一个还没到的时刻。</li>
 *   <li><b>分组</b>：今天 / 昨天 / 更早。分组在 Mapper 里定，面板只按 {@link AiHistoryItemUi.Group}
 *       换标题，不在视图里算日历。</li>
 * </ul>
 */
public final class AiHistoryUiMapper {

    /** 标题最多 16 码点（规格 §2.2）。 */
    static final int TITLE_MAX_CODE_POINTS = 16;

    /** 摘要最多 12 码点（规格 §2.2）。 */
    static final int PREVIEW_MAX_CODE_POINTS = 12;

    /** 超过 7 天就不再说"N 天前"，直接给日期。 */
    private static final int RELATIVE_DAYS_LIMIT = 7;

    /** 时间只精确到分钟；固定 {@link Locale#ROOT}，避免某些区域把冒号换成别的字符。 */
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * 一次翻完整张列表。
     *
     * <p>排序放在这里，不依赖仓库 {@code summaries()} 的返回顺序：面板要的是"最近改过的在最上面"，
     * 而仓库只保证"它自己认为的顺序"。同一毫秒的两条按 id 排，保证多次渲染不会来回跳。
     * 排序是全局降序，所以分组天然按 今天 → 昨天 → 更早 出现。</p>
     */
    @NonNull
    public List<AiHistoryItemUi> map(@NonNull List<AiConversationSummary> summaries,
                                     @Nullable String currentId,
                                     long nowMillis,
                                     @NonNull ZoneId zone) {
        List<AiConversationSummary> ordered = new ArrayList<>(summaries);
        ordered.sort(Comparator.comparingLong(AiConversationSummary::updatedAtMillis).reversed()
                .thenComparing(AiConversationSummary::id));
        List<AiHistoryItemUi> rows = new ArrayList<>(ordered.size());
        for (AiConversationSummary summary : ordered) {
            long updatedAt = summary.updatedAtMillis();
            When when = when(nowMillis, updatedAt, zone);
            rows.add(new AiHistoryItemUi(
                    summary.id(),
                    title(summary.title()),
                    preview(summary.preview()),
                    when.label(),
                    summary.id().equals(currentId),
                    when.group()));
        }
        return List.copyOf(rows);
    }

    /**
     * 标题：没名字时给一句兜底；有名字时按 16 码点截断。
     *
     * <p>兜底文案也要过一遍截断：它是资源里的一句话，翻译变长时同样不能把行撑开。</p>
     */
    @NonNull
    static AiHistoryText title(@Nullable String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return AiHistoryText.of(R.string.ai_history_title_untitled);
        }
        return AiHistoryText.raw(truncate(value, TITLE_MAX_CODE_POINTS));
    }

    /**
     * 摘要：压平换行后按 12 码点截断；没有内容时返回 {@code null}——整行只显示标题，行高回到 56dp。
     *
     * <p>截断按<b>码点</b>数，不按 {@code char} 数：emoji 与生僻字都是两个 char，按 char 切会把它们劈成半个。</p>
     */
    @Nullable
    static AiHistoryText preview(@Nullable String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
        if (value.isEmpty()) {
            return null;
        }
        return AiHistoryText.raw(truncate(value, PREVIEW_MAX_CODE_POINTS));
    }

    /**
     * 一条记录的"分组 + 时刻"。两件事共用<b>一次</b>自然日计算。
     *
     * <p>为什么不拆成 {@code group()} 与 {@code time()} 两个函数各算一遍：那样两处日历判断早晚会分叉，
     * 出现"分组是今天、时刻是 3 天前"这种自相矛盾的行，而这种错只在跨零点时才看得见。
     * 合成一个纯函数之后，规则可以被直接单测（今天 / 昨天 / N 天前 / 跨年），不必等界面。</p>
     */
    public record When(@NonNull AiHistoryItemUi.Group group, @NonNull AiHistoryText label) {
    }

    /**
     * 时刻与分组的唯一判定：
     * <ul>
     *   <li>今天（含未来时间）→ 分组「今天」，时刻 {@code HH:mm}；</li>
     *   <li>1 天前 → 分组「昨天」，时刻「1 天前」；</li>
     *   <li>2–6 天前 → 分组「更早」，时刻「N 天前」；</li>
     *   <li>7 天及更早 → 分组「更早」，时刻「M月d日」。</li>
     * </ul>
     * 全部按自然日算，不看"几小时前"：凌晨 0:10 看昨晚 23:50 的记录，那是"昨天"，
     * 说成"20 分钟前"会让它跑到「今天」分组里，与分组标题打架。
     */
    @NonNull
    static When when(long nowMillis, long thenMillis, @NonNull ZoneId zone) {
        long days = daysBetween(nowMillis, thenMillis, zone);
        if (days <= 0) {
            return new When(AiHistoryItemUi.Group.TODAY,
                    AiHistoryText.of(R.string.ai_history_time_clock, timeOfDay(thenMillis, zone)));
        }
        if (days == 1) {
            return new When(AiHistoryItemUi.Group.YESTERDAY,
                    AiHistoryText.of(R.string.ai_history_time_days, 1));
        }
        if (days < RELATIVE_DAYS_LIMIT) {
            return new When(AiHistoryItemUi.Group.EARLIER,
                    AiHistoryText.of(R.string.ai_history_time_days, (int) days));
        }
        LocalDate then = LocalDate.ofInstant(Instant.ofEpochMilli(thenMillis), zone);
        return new When(AiHistoryItemUi.Group.EARLIER,
                AiHistoryText.of(R.string.ai_history_time_date,
                        then.getMonthValue(), then.getDayOfMonth()));
    }

    /** 自然日差：未来时间算 0（今天），免得把"还没到"排到分组外面去。 */
    private static long daysBetween(long nowMillis, long thenMillis, @NonNull ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        LocalDate then = LocalDate.ofInstant(Instant.ofEpochMilli(thenMillis), zone);
        return Math.max(0L, ChronoUnit.DAYS.between(then, today));
    }

    @NonNull
    private static String timeOfDay(long millis, @NonNull ZoneId zone) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(TIME_OF_DAY);
    }

    /** 按码点截断并补省略号；没超长就原样返回。 */
    @NonNull
    private static String truncate(@NonNull String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end).strip() + "…";
    }
}
