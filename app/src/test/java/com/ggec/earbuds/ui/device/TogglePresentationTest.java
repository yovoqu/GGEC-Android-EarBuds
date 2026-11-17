package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;

import org.junit.Test;

import java.util.Optional;

/**
 * 开关呈现规则：**值未知只影响「值怎么显示」，不影响「控件是否存在」**。
 *
 * <p>这条锁死的是功能回归：协议只有 Set、没有 Query 的那批功能开关（VOLUME_ADAPTIVE /
 * WEAR_DETECT / VOICE_WAKEUP / FALL_ALERT / VOLUME_REMIND / INTERPRET）永远拿不到上报值，
 * 如果「未上报」时取消开关，它们就永远发不出写入。</p>
 *
 * <p>呈现是纯函数：写入状态（{@link DeviceSettingsUiState.WriteState}）与两个值都是入参，
 * 本类不持有任何未确认副本。</p>
 */
public final class TogglePresentationTest {

    private static final DeviceSettingsUiState.WriteState IDLE =
            DeviceSettingsUiState.WriteState.IDLE;
    private static final DeviceSettingsUiState.WriteState IN_FLIGHT =
            DeviceSettingsUiState.WriteState.IN_FLIGHT;
    private static final DeviceSettingsUiState.WriteState FAILED =
            DeviceSettingsUiState.WriteState.FAILED;

    @Test
    public void unknownValueStillYieldsAnEnabledSwitch() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.empty(), true);

        assertTrue("未上报必须仍然给出可写开关", presentation.enabled());
        assertTrue(presentation.unknown());
        assertFalse("未知时取值关作为未确认呈现", presentation.position());
        assertSame(IDLE, presentation.writeState());
    }

    @Test
    public void unknownValueKeepsTheUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.of(true), true);

        assertTrue(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void reportedValueWinsOverTheUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.of(true), Optional.of(false), true);

        assertFalse(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
    }

    @Test
    public void reportedOffIsNotUnknown() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.of(false), Optional.empty(), true);

        assertFalse(presentation.unknown());
        assertFalse(presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void notWritableKeepsTheSwitchButDisablesIt() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.empty(), false);

        assertFalse(presentation.enabled());
        assertTrue(presentation.unknown());
    }

    @Test
    public void inFlightWriteDisablesFurtherWrites() {
        TogglePresentation presentation =
                TogglePresentation.of(IN_FLIGHT, Optional.of(false), Optional.of(true), true);

        assertFalse("写入未落地前不重复下发", presentation.enabled());
        assertSame(IN_FLIGHT, presentation.writeState());
        assertFalse(presentation.unknown());
        assertFalse("上报值优先，不显示未确认值", presentation.position());
    }

    @Test
    public void failedWriteFallsBackToTheReportedValue() {
        TogglePresentation presentation =
                TogglePresentation.of(FAILED, Optional.of(true), Optional.empty(), true);

        assertSame(FAILED, presentation.writeState());
        assertTrue(presentation.enabled());
        assertTrue("失败后回到设备上报值", presentation.position());
    }

    @Test
    public void nullInputsAreContractViolations() {
        org.junit.Assert.assertThrows(NullPointerException.class,
                () -> TogglePresentation.of(null, Optional.empty(), Optional.empty(), true));
    }
}
