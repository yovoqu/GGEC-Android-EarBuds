package com.ggec.earbuds.ui.ai.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 相对时间的结构化结果，由页面映射成"刚刚""3 分钟前"这样的文字。
 */
public record AiTimeLabel(Kind kind, int first, int second) {

    public enum Kind {
        NOW,
        MINUTES,
        HOURS,
        YESTERDAY,
        DATE
    }

    public static AiTimeLabel of(long nowMillis, long thenMillis, ZoneId zone) {
        long elapsed = Math.max(0, nowMillis - thenMillis);
        long minutes = elapsed / 60_000L;
        if (minutes < 1) {
            return new AiTimeLabel(Kind.NOW, 0, 0);
        }
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        LocalDate then = LocalDate.ofInstant(Instant.ofEpochMilli(thenMillis), zone);
        if (then.equals(today)) {
            return minutes < 60
                    ? new AiTimeLabel(Kind.MINUTES, (int) minutes, 0)
                    : new AiTimeLabel(Kind.HOURS, (int) (minutes / 60), 0);
        }
        if (then.equals(today.minusDays(1))) {
            return new AiTimeLabel(Kind.YESTERDAY, 0, 0);
        }
        return new AiTimeLabel(Kind.DATE, then.getMonthValue(), then.getDayOfMonth());
    }
}
