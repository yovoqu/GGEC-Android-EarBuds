package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiConversationSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 历史分组（HIS-02）：按最后更新的本地日期距今天数：0 今天、1 昨天、2–6 过去 7 天、7–29 过去 30 天、其余更早。
 * 空组不返回；组内按时间倒序。
 */
public final class AiHistoryGrouping {

    public enum Bucket {
        TODAY,
        YESTERDAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        EARLIER
    }

    public record Group(Bucket bucket, List<AiConversationSummary> items) {
        public Group {
            items = List.copyOf(items);
        }
    }

    private AiHistoryGrouping() {
    }

    public static Bucket bucket(long timeMillis, long nowMillis, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        LocalDate day = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate();
        long daysAgo = ChronoUnit.DAYS.between(day, today);
        if (daysAgo <= 0) {
            return Bucket.TODAY;
        }
        if (daysAgo == 1) {
            return Bucket.YESTERDAY;
        }
        if (daysAgo < 7) {
            return Bucket.LAST_7_DAYS;
        }
        if (daysAgo < 30) {
            return Bucket.LAST_30_DAYS;
        }
        return Bucket.EARLIER;
    }

    public static List<Group> group(List<AiConversationSummary> summaries, long nowMillis, ZoneId zone) {
        List<AiConversationSummary> sorted = new ArrayList<>(summaries);
        sorted.sort(Comparator.comparingLong(AiConversationSummary::updatedAtMillis).reversed());
        Map<Bucket, List<AiConversationSummary>> grouped = new EnumMap<>(Bucket.class);
        for (AiConversationSummary summary : sorted) {
            grouped.computeIfAbsent(bucket(summary.updatedAtMillis(), nowMillis, zone), b -> new ArrayList<>())
                    .add(summary);
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Bucket, List<AiConversationSummary>> entry : grouped.entrySet()) {
            groups.add(new Group(entry.getKey(), entry.getValue()));
        }
        return groups;
    }
}
