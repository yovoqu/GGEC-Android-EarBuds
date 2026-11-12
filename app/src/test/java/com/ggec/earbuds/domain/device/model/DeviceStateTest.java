package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 设备控制值的读取契约：唯一读取口返回空而不是哨兵值，也不因为缺值抛异常。 */
public final class DeviceStateTest {

    @Test
    public void valueReturnsEmptyWhenCapabilityWasNeverReported() {
        DeviceState state = new DeviceState(Map.of());

        assertEquals(Optional.empty(), state.value("GAME_MODE"));
    }

    @Test
    public void valueReturnsTheReportedValueOfEveryShape() {
        DeviceState state = new DeviceState(Map.of(
                "GAME_MODE", new ControlValue.Toggle(true),
                "VOLUME", new ControlValue.Level(7),
                "EQ_MODE", new ControlValue.Choice("music"),
                "CUSTOM_EQ", new ControlValue.Curve(List.of(0, 2, -1))));

        assertEquals(Optional.of(new ControlValue.Toggle(true)), state.value("GAME_MODE"));
        assertEquals(Optional.of(new ControlValue.Level(7)), state.value("VOLUME"));
        assertEquals(Optional.of(new ControlValue.Choice("music")), state.value("EQ_MODE"));
        assertEquals(Optional.of(new ControlValue.Curve(List.of(0, 2, -1))), state.value("CUSTOM_EQ"));
    }

    @Test
    public void nullCapabilityIdIsRejectedInsteadOfReadingAsAbsent() {
        DeviceState state = new DeviceState(Map.of("GAME_MODE", new ControlValue.Toggle(true)));

        assertEquals("capabilityId", assertThrows(NullPointerException.class,
                () -> state.value(null)).getMessage());
    }

    @Test
    public void stateIsAnImmutableCopyOfTheReportedValues() {
        Map<String, ControlValue> source = new HashMap<>();
        source.put("GAME_MODE", new ControlValue.Toggle(false));
        DeviceState state = new DeviceState(source);

        source.put("GAME_MODE", new ControlValue.Toggle(true));
        source.put("EQ_MODE", new ControlValue.Choice("movie"));

        assertEquals(Optional.of(new ControlValue.Toggle(false)), state.value("GAME_MODE"));
        assertEquals(Optional.empty(), state.value("EQ_MODE"));
        assertTrue(state.values().size() == 1);
    }

    @Test
    public void nullValuesMapIsRejected() {
        assertEquals("values", assertThrows(NullPointerException.class,
                () -> new DeviceState(null)).getMessage());
    }

    @Test
    public void curvePointsAreCopiedSoLaterMutationDoesNotLeak() {
        java.util.ArrayList<Integer> points = new java.util.ArrayList<>(List.of(1, 2, 3));
        DeviceState state = new DeviceState(Map.of("CUSTOM_EQ", new ControlValue.Curve(points)));

        points.set(0, 9);

        ControlValue stored = state.value("CUSTOM_EQ").orElseThrow();
        assertSame(ControlValue.Curve.class, stored.getClass());
        assertEquals(List.of(1, 2, 3), ((ControlValue.Curve) stored).points());
    }
}
