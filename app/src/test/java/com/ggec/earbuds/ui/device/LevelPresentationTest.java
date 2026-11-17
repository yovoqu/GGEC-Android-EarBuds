package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;

import org.junit.Test;

import java.util.OptionalInt;

/** 档位呈现规则：值未知不影响可写性（与开关同一条裁定），呈现状态全部来自入参。 */
public final class LevelPresentationTest {

    private static final DeviceSettingsUiState.WriteState IDLE =
            DeviceSettingsUiState.WriteState.IDLE;
    private static final DeviceSettingsUiState.WriteState IN_FLIGHT =
            DeviceSettingsUiState.WriteState.IN_FLIGHT;
    private static final DeviceSettingsUiState.WriteState CONFIRMED =
            DeviceSettingsUiState.WriteState.CONFIRMED;

    @Test
    public void unknownValueStillYieldsAWritableSlider() {
        LevelPresentation presentation = LevelPresentation.of(IDLE, OptionalInt.empty(),
                OptionalInt.empty(), 0, true);

        assertTrue("未上报必须仍然可写", presentation.enabled());
        assertTrue(presentation.unknown());
        assertEquals("未知时先落在下界", 0, presentation.value());
        assertSame(IDLE, presentation.writeState());
    }

    @Test
    public void unknownValueKeepsTheUnconfirmedChoice() {
        LevelPresentation presentation = LevelPresentation.of(IDLE, OptionalInt.empty(),
                OptionalInt.of(7), 0, true);

        assertTrue(presentation.unknown());
        assertEquals(7, presentation.value());
        assertTrue(presentation.enabled());
    }

    @Test
    public void reportedValueWinsOverTheUnconfirmedChoice() {
        LevelPresentation presentation = LevelPresentation.of(IDLE, OptionalInt.of(3),
                OptionalInt.of(9), 0, true);

        assertFalse(presentation.unknown());
        assertEquals(3, presentation.value());
    }

    @Test
    public void notWritableKeepsTheSliderButDisablesIt() {
        LevelPresentation presentation = LevelPresentation.of(IDLE, OptionalInt.of(5),
                OptionalInt.empty(), 0, false);

        assertFalse(presentation.enabled());
        assertFalse(presentation.unknown());
        assertEquals(5, presentation.value());
    }

    @Test
    public void inFlightWriteDisablesFurtherWrites() {
        LevelPresentation presentation = LevelPresentation.of(IN_FLIGHT,
                OptionalInt.empty(), OptionalInt.of(8), 0, true);

        assertFalse(presentation.enabled());
        assertSame(IN_FLIGHT, presentation.writeState());
        assertEquals("未确认值仍然显示", 8, presentation.value());
    }

    @Test
    public void confirmedWriteKeepsTheUsersValueWhenTheDeviceIsSilent() {
        LevelPresentation presentation = LevelPresentation.of(CONFIRMED,
                OptionalInt.empty(), OptionalInt.of(8), 0, true);

        assertSame(CONFIRMED, presentation.writeState());
        assertEquals(8, presentation.value());
        assertTrue(presentation.unknown());
    }

    @Test
    public void nullInputsAreContractViolations() {
        org.junit.Assert.assertThrows(NullPointerException.class,
                () -> LevelPresentation.of(null, OptionalInt.empty(), OptionalInt.empty(), 0, true));
    }
}
