package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/**
 * 控件快照的构造契约：曲线长度是编程不变量（十个频段），null 输入在构造点抛出；
 * 「没有自定义曲线」写成十个 0，「没有手势绑定」写成空表，均由调用方显式给出。
 */
public final class DeviceControlsTest {

    @Test
    public void nullCustomEqNameIsRejectedAtConstruction() {
        assertEquals("customEqName", assertThrows(NullPointerException.class,
                () -> controls(null, bands(), Map.of())).getMessage());
    }

    @Test
    public void nullCustomEqGainsIsRejectedAtConstruction() {
        assertEquals("customEqGains", assertThrows(NullPointerException.class,
                () -> controls("", null, Map.of())).getMessage());
    }

    @Test
    public void gainsLengthMustMatchBandCountInsteadOfBeingTruncatedOrPadded() {
        int tooShort = DeviceControls.CUSTOM_EQ_BANDS - 1;
        IllegalArgumentException shortCurve = assertThrows(IllegalArgumentException.class,
                () -> controls("", new float[tooShort], Map.of()));
        assertEquals("customEqGains must have " + DeviceControls.CUSTOM_EQ_BANDS
                + " bands but has " + tooShort, shortCurve.getMessage());

        int tooLong = DeviceControls.CUSTOM_EQ_BANDS + 1;
        IllegalArgumentException longCurve = assertThrows(IllegalArgumentException.class,
                () -> controls("", new float[tooLong], Map.of()));
        assertEquals("customEqGains must have " + DeviceControls.CUSTOM_EQ_BANDS
                + " bands but has " + tooLong, longCurve.getMessage());
    }

    @Test
    public void nullGestureMapIsRejectedAtConstruction() {
        assertEquals("gestures", assertThrows(NullPointerException.class,
                () -> controls("", bands(), null)).getMessage());
    }

    @Test
    public void gestureEntriesMustCarryNonNullAction() {
        Map<GestureSlot, GestureAction> holed = new EnumMap<>(GestureSlot.class);
        holed.put(GestureSlot.LONG, null);

        assertEquals("gestures[LONG]", assertThrows(NullPointerException.class,
                () -> controls("", bands(), holed)).getMessage());
    }

    @Test
    public void nullSlotIsRejectedOnLookupAndOnBinding() {
        DeviceControls empty = DeviceControls.empty();

        assertEquals("slot", assertThrows(NullPointerException.class,
                () -> empty.gesture(null)).getMessage());
        assertEquals("slot", assertThrows(NullPointerException.class,
                () -> empty.withGesture(null, GestureAction.PLAY_PAUSE)).getMessage());
    }

    @Test
    public void emptyControlsCarryFullLengthCurveAndNoBindings() {
        DeviceControls empty = DeviceControls.empty();

        assertEquals(DeviceControls.CUSTOM_EQ_BANDS, empty.customEqGains().length);
        assertEquals("", empty.customEqName());
        assertTrue(empty.gestures().isEmpty());
        assertNull(empty.eqPreset());
        assertNull(empty.gesture(GestureSlot.LONG));
    }

    @Test
    public void customEqIsCopiedAndNameIsStripped() {
        float[] source = bands();
        source[0] = 3.5f;

        DeviceControls tuned = DeviceControls.empty().withCustomEq("  Mine  ", source);
        source[0] = -9f;

        assertEquals("Mine", tuned.customEqName());
        assertEquals(EqPreset.CUSTOM, tuned.eqPreset());
        assertTrue(tuned.customEqSaved());
        assertEquals(3.5f, tuned.customEqGains()[0], 0f);
    }

    @Test
    public void nullActionUnbindsTheSlot() {
        DeviceControls bound = DeviceControls.empty()
                .withGesture(GestureSlot.LONG, GestureAction.PLAY_PAUSE);

        assertEquals(GestureAction.PLAY_PAUSE, bound.gesture(GestureSlot.LONG));
        assertNull(bound.withGesture(GestureSlot.LONG, null).gesture(GestureSlot.LONG));
    }

    private static float[] bands() {
        return new float[DeviceControls.CUSTOM_EQ_BANDS];
    }

    private static DeviceControls controls(String customEqName, float[] customEqGains,
                                           Map<GestureSlot, GestureAction> gestures) {
        return new DeviceControls(null, false, customEqName, customEqGains, null,
                false, false, false, false, false, false, false, false, false, false, gestures);
    }
}
