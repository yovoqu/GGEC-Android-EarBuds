package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.OptionalInt;

/** 档位呈现规则：值未知不影响可写性（与开关同一条裁定）。 */
public final class LevelPresentationTest {

    @Test
    public void unknownValueStillYieldsAWritableSlider() {
        LevelPresentation presentation =
                LevelPresentation.of(OptionalInt.empty(), OptionalInt.empty(), 0, true);

        assertTrue("未上报必须仍然可写", presentation.enabled());
        assertTrue(presentation.unknown());
        assertEquals("未知时先落在下界", 0, presentation.value());
    }

    @Test
    public void unknownValueKeepsTheUsersUnconfirmedChoice() {
        LevelPresentation presentation =
                LevelPresentation.of(OptionalInt.empty(), OptionalInt.of(7), 0, true);

        assertTrue(presentation.unknown());
        assertEquals(7, presentation.value());
        assertTrue(presentation.enabled());
    }

    @Test
    public void reportedValueWinsOverTheUnconfirmedChoice() {
        LevelPresentation presentation =
                LevelPresentation.of(OptionalInt.of(3), OptionalInt.of(9), 0, true);

        assertFalse(presentation.unknown());
        assertEquals(3, presentation.value());
    }

    @Test
    public void notWritableKeepsTheSliderButDisablesIt() {
        LevelPresentation presentation =
                LevelPresentation.of(OptionalInt.of(5), OptionalInt.empty(), 0, false);

        assertFalse(presentation.enabled());
        assertFalse(presentation.unknown());
        assertEquals(5, presentation.value());
    }

    @Test
    public void nullInputsAreAbsentValues() {
        LevelPresentation presentation = LevelPresentation.of(null, null, 2, true);

        assertTrue(presentation.unknown());
        assertEquals(2, presentation.value());
        assertTrue(presentation.enabled());
    }
}
