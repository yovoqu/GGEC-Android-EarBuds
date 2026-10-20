package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;

/** 时间和 id 都由测试掌控，断言才能写成确定的值。 */
public final class ManualClock implements AiClock, AiIdGenerator {

    public long now = 1_000L;
    private int sequence;

    @Override
    public long now() {
        return now;
    }

    public void advance(long millis) {
        now += millis;
    }

    @Override
    public String next() {
        sequence++;
        return String.format("n%07d", sequence);
    }
}
