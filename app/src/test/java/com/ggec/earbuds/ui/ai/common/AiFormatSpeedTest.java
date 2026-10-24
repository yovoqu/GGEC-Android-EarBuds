package com.ggec.earbuds.ui.ai.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 生成速率的换算口径：只用确证数据，数据不足就不显示。 */
public final class AiFormatSpeedTest {

    @Test
    public void aNormalAnswerGetsItsAverageRate() {
        // 170 tokens / 3.3 s ≈ 52 tok/s
        assertEquals(52, AiFormat.tokensPerSecond(170, 3300L));
    }

    @Test
    public void withoutTokensThereIsNoRate() {
        assertEquals(-1, AiFormat.tokensPerSecond(0, 3300L));
        assertEquals(-1, AiFormat.tokensPerSecond(-1, 3300L));
    }

    @Test
    public void aTooShortTurnIsNotMeasured() {
        // 200ms 内的回答算不出有意义的速率，宁可不显示。
        assertEquals(-1, AiFormat.tokensPerSecond(5, 200L));
    }

    @Test
    public void aVeryFastShortAnswerStillReportsSomethingSane() {
        assertEquals(100, AiFormat.tokensPerSecond(30, 300L));
    }
}