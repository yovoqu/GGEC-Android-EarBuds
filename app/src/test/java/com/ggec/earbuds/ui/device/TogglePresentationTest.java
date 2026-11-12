package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

/**
 * 开关呈现规则：**值未知只影响「值怎么显示」，不影响「控件是否存在」**。
 *
 * <p>这条锁死的是功能回归：协议只有 Set、没有 Query 的那批功能开关（VOLUME_ADAPTIVE /
 * WEAR_DETECT / VOICE_WAKEUP / FALL_ALERT / VOLUME_REMIND / INTERPRET）永远拿不到上报值，
 * 如果「未上报」时取消开关，它们就永远发不出写入。</p>
 */
public final class TogglePresentationTest {

    @Test
    public void unknownValueStillYieldsAnEnabledSwitch() {
        TogglePresentation presentation =
                TogglePresentation.of(Optional.empty(), Optional.empty(), true);

        assertTrue("未上报必须仍然给出可写开关", presentation.enabled());
        assertTrue(presentation.unknown());
        assertFalse("未知时取值关作为未确认呈现", presentation.position());
    }

    @Test
    public void unknownValueKeepsTheUsersUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(Optional.empty(), Optional.of(true), true);

        assertTrue(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void reportedValueWinsOverTheUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(Optional.of(true), Optional.of(false), true);

        assertFalse(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
    }

    @Test
    public void reportedOffIsNotUnknown() {
        TogglePresentation presentation =
                TogglePresentation.of(Optional.of(false), Optional.empty(), true);

        assertFalse(presentation.unknown());
        assertFalse(presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void notWritableKeepsTheSwitchButDisablesIt() {
        TogglePresentation presentation =
                TogglePresentation.of(Optional.empty(), Optional.empty(), false);

        assertFalse(presentation.enabled());
        assertTrue(presentation.unknown());
    }

    @Test
    public void nullInputsAreAbsentValues() {
        TogglePresentation presentation = TogglePresentation.of(null, null, true);

        assertTrue(presentation.unknown());
        assertFalse(presentation.position());
        assertTrue(presentation.enabled());
    }
}
