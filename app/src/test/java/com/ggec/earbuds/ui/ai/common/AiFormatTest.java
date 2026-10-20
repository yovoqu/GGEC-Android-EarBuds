package com.ggec.earbuds.ui.ai.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.ZoneId;

public class AiFormatTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long NOON = java.time.ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, ZONE)
            .toInstant().toEpochMilli();

    @Test
    public void tokenCountsUseTheVendorsOwnRounding() {
        assertEquals("512", AiFormat.tokens(512));
        assertEquals("8K", AiFormat.tokens(8_192));
        assertEquals("16K", AiFormat.tokens(16_000));
        assertEquals("128K", AiFormat.tokens(128_000));
        assertEquals("128K", AiFormat.tokens(131_072));
        assertEquals("200K", AiFormat.tokens(200_000));
        assertEquals("1M", AiFormat.tokens(1_000_000));
        assertEquals("1M", AiFormat.tokens(1_048_576));
        assertEquals("2M", AiFormat.tokens(2_000_000));
    }

    @Test
    public void durationsSwitchFromMillisecondsToSeconds() {
        assertEquals("0 ms", AiFormat.duration(-5));
        assertEquals("320 ms", AiFormat.duration(320));
        assertEquals("999 ms", AiFormat.duration(999));
        assertEquals("1.0 s", AiFormat.duration(1_000));
        assertEquals("3.2 s", AiFormat.duration(3_240));
    }

    @Test
    public void keysAreMaskedWithoutRevealingShortOnes() {
        assertEquals("sk-••••••••cdef", AiFormat.maskKey("sk-live-1234567890abcdef"));
        assertEquals("••••••••", AiFormat.maskKey("12345678"));
        assertEquals("••••••••", AiFormat.maskKey("abc"));
    }

    @Test
    public void relativeTimesFollowTheCalendarDay() {
        assertEquals(AiTimeLabel.Kind.NOW, AiTimeLabel.of(NOON, NOON - 30_000, ZONE).kind());
        assertEquals(AiTimeLabel.Kind.NOW, AiTimeLabel.of(NOON, NOON + 5_000, ZONE).kind());

        AiTimeLabel minutes = AiTimeLabel.of(NOON, NOON - 5 * 60_000L, ZONE);
        assertEquals(AiTimeLabel.Kind.MINUTES, minutes.kind());
        assertEquals(5, minutes.first());

        AiTimeLabel hours = AiTimeLabel.of(NOON, NOON - 3 * 3_600_000L - 120_000L, ZONE);
        assertEquals(AiTimeLabel.Kind.HOURS, hours.kind());
        assertEquals(3, hours.first());

        long lateLastNight = NOON - 13 * 3_600_000L;
        assertEquals("less than 24 hours ago but yesterday on the calendar",
                AiTimeLabel.Kind.YESTERDAY, AiTimeLabel.of(NOON, lateLastNight, ZONE).kind());

        AiTimeLabel older = AiTimeLabel.of(NOON, NOON - 5 * 86_400_000L, ZONE);
        assertEquals(AiTimeLabel.Kind.DATE, older.kind());
        assertEquals(10, older.first());
        assertEquals(11, older.second());
    }
}
