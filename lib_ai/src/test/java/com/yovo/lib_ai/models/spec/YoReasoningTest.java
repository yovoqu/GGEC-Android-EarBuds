package com.yovo.lib_ai.models.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 推理面：会不会想、能不能关、力度和预算能不能调。
 */
public final class YoReasoningTest {

    @Test
    public void onlyThreeModesProduceThinking() {
        assertFalse(YoReasoningMode.UNKNOWN.reasons());
        assertFalse(YoReasoningMode.NONE.reasons());
        assertTrue(YoReasoningMode.SUPPORTED.reasons());
        assertTrue(YoReasoningMode.ALWAYS.reasons());
        assertTrue(YoReasoningMode.SWITCHABLE.reasons());
    }

    @Test
    public void switchableModelsCanBeTurnedOffAndAlwaysOnOnesCannot() {
        YoReasoning hybrid = new YoReasoning(YoReasoningMode.SWITCHABLE,
                EnumSet.of(YoReasoningEffort.LOW, YoReasoningEffort.HIGH), true, YoSpecSource.REPORTED);
        assertTrue(hybrid.reasons());
        assertTrue(hybrid.canDisable());
        assertTrue(hybrid.effortAdjustable());
        assertTrue(hybrid.budgetAdjustable());
        assertTrue(hybrid.confirmed());

        YoReasoning always = YoReasoning.of(YoReasoningMode.ALWAYS, YoSpecSource.INFERRED);
        assertTrue(always.reasons());
        assertFalse("强制思考的模型关不掉", always.canDisable());
        assertFalse(always.effortAdjustable());
        assertFalse("名字上看着像推理，还没人确认", always.confirmed());
    }

    @Test
    public void unknownAndNoneNeverCountAsReasoning() {
        assertFalse(YoReasoning.UNKNOWN.reasons());
        assertFalse(YoReasoning.UNKNOWN.confirmed());
        YoReasoning none = YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.USER);
        assertFalse(none.reasons());
        assertFalse(none.confirmed());
    }

    @Test
    public void effortsAreKeptReadOnlyAndCanBeReplaced() {
        YoReasoning base = YoReasoning.of(YoReasoningMode.SUPPORTED, YoSpecSource.REPORTED);
        assertTrue(base.efforts().isEmpty());

        YoReasoning withLevels = base.withEfforts(List.of(YoReasoningEffort.MEDIUM, YoReasoningEffort.HIGH));
        assertEquals(Set.of(YoReasoningEffort.MEDIUM, YoReasoningEffort.HIGH), withLevels.efforts());
        assertTrue("原对象不被改写", base.efforts().isEmpty());
        assertTrue(withLevels.withBudgetAdjustable(true).budgetAdjustable());
        assertFalse(withLevels.budgetAdjustable());
        assertThrows(UnsupportedOperationException.class,
                () -> withLevels.efforts().add(YoReasoningEffort.MAX));
    }

    @Test
    public void effortWireNamesMatchWhatRequestsCanSend() {
        Set<String> wires = new HashSet<>();
        for (YoReasoningEffort effort : YoReasoningEffort.values()) {
            wires.add(effort.wire());
        }
        // 与 YoChatCompletionRequest.Builder#reasoningEffort 接受的那一组一致。
        assertEquals(Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max"), wires);
        assertSame(YoReasoningEffort.HIGH, YoReasoningEffort.fromWire("HIGH"));
        assertSame(YoReasoningEffort.XHIGH, YoReasoningEffort.fromWire(" xhigh "));
        assertNull(YoReasoningEffort.fromWire("ultra"));
        assertNull(YoReasoningEffort.fromWire(null));
    }
}
