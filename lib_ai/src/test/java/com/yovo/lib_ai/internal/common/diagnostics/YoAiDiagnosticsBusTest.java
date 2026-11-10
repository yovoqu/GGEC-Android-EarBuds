package com.yovo.lib_ai.internal.common.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiHttpException;

import org.junit.Test;

/** {@link YoAiDiagnosticsBus} 行为：分类、时间线、dump。 */
public class YoAiDiagnosticsBusTest {

    @Test
    public void recordsHttpFailureWithKindAndTimeline() {
        boolean previous = YoAiLogger.isEnabled();
        YoAiLogger.setEnabled(false); // 单测 JVM 无 android.util.Log，禁用以避免触达 YoLog
        try {
            YoAiDiagnosticsBus bus = new YoAiDiagnosticsBus(8);
            YoAiHttpException failure =
                    new YoAiHttpException("rate limited", 429, "req-1", "rate_limit", "body");

            bus.recordFailure(failure);

            assertEquals(YoAiErrorKind.RATE_LIMITED, YoAiErrors.kindOf(failure));
            assertEquals(1, bus.timelineSnapshot().size());
            assertTrue(bus.timelineSnapshot().get(0).contains("RATE_LIMITED"));
            assertTrue(bus.dumpReport().contains("events=1"));

            bus.recordEvent("client.close", "YoAiClient");
            assertEquals(2, bus.timelineSnapshot().size());
        } finally {
            YoAiLogger.setEnabled(previous);
        }
    }

    @Test
    public void timelineIsBoundedByCapacity() {
        boolean previous = YoAiLogger.isEnabled();
        YoAiLogger.setEnabled(false);
        try {
            YoAiDiagnosticsBus bus = new YoAiDiagnosticsBus(2);
            bus.recordEvent("e1", 1);
            bus.recordEvent("e2", 2);
            bus.recordEvent("e3", 3);
            assertEquals(2, bus.timelineSnapshot().size());
            assertTrue(bus.timelineSnapshot().get(1).contains("e3"));
        } finally {
            YoAiLogger.setEnabled(previous);
        }
    }
}
